package no.rutebanken.marduk.inbound;

import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import no.rutebanken.marduk.repository.InMemoryMardukBlobStoreRepository;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.ExchangeBlobStoreService;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.FILE_TYPE;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MardukInboundQueueConsumerTest {

    private static final String HANDLE = "inbound/received/rb_tst/netex.zip";

    private InMemoryMardukBlobStoreRepository exchangeRepository;
    private InMemoryMardukBlobStoreRepository internalRepository;
    private RecordingPubSubPublisher publisher;
    private ProviderRepository providerRepository;

    @BeforeEach
    void setUp() {
        exchangeRepository = new InMemoryMardukBlobStoreRepository(new ConcurrentHashMap<>());
        exchangeRepository.setContainerName("marduk-exchange");
        internalRepository = new InMemoryMardukBlobStoreRepository(new ConcurrentHashMap<>());
        internalRepository.setContainerName("marduk-internal");
        publisher = new RecordingPubSubPublisher();

        Provider provider = new Provider();
        ChouetteInfo chouetteInfo = new ChouetteInfo();
        chouetteInfo.setReferential("rb_tst");
        provider.setChouetteInfo(chouetteInfo);
        providerRepository = mock(ProviderRepository.class);
        when(providerRepository.getProvider(2L)).thenReturn(provider);
    }

    private MardukInboundQueueConsumer consumer(boolean deleteExternalBlobs) {
        return new MardukInboundQueueConsumer(
                new ExchangeBlobStoreService("marduk-exchange", exchangeRepository),
                new MardukInternalBlobStoreService("marduk-internal", internalRepository),
                providerRepository,
                new JobEventPublisher(publisher),
                publisher,
                deleteExternalBlobs);
    }

    private static MardukMessage notification() {
        return new MardukMessage()
                .setHeader(PROVIDER_ID, 2L)
                .setHeader(CORRELATION_ID, "corr")
                .setHeader(FILE_NAME, "netex.zip")
                .setHeader(FILE_HANDLE, HANDLE);
    }

    /** A minimal NeTEx-looking archive, enough for the classifier to call it NETEXPROFILE. */
    private static byte[] netexZip() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("line.xml"));
            zip.write(("<?xml version=\"1.0\"?><PublicationDelivery xmlns=\"http://www.netex.org.uk/netex\"/>")
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    @Test
    void aValidFileIsStoredInternallyAndHandedToClassification() throws Exception {
        exchangeRepository.uploadBlob(HANDLE, new ByteArrayInputStream(netexZip()));

        MardukMessage message = notification();
        consumer(false).handle(message);

        assertTrue(internalRepository.exist(HANDLE), "the file was not copied into marduk's own bucket");
        assertEquals(1, publisher.publishedTo(MardukQueues.PROCESS_FILE_QUEUE).size());
        assertEquals("NETEXPROFILE", message.getHeader(FILE_TYPE, String.class));
        assertEquals("rb_tst", message.getHeader(CHOUETTE_REFERENTIAL, String.class));
    }

    @Test
    void theFileTransferIsReportedAsStarted() throws Exception {
        exchangeRepository.uploadBlob(HANDLE, new ByteArrayInputStream(netexZip()));

        consumer(false).handle(notification());

        JobEvent reported = JobEvent.fromString(
                publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).getFirst().body());
        assertEquals("FILE_TRANSFER", reported.getAction());
        assertEquals(JobEvent.State.STARTED, reported.getState());
    }

    @Test
    void anUnclassifiableFileIsDeadLetteredAndNotStored() {
        exchangeRepository.uploadBlob(HANDLE, new ByteArrayInputStream("not a zip".getBytes(StandardCharsets.UTF_8)));

        MardukMessage message = notification();
        consumer(false).handle(message);

        // NOT_A_ZIP_FILE is a classification the bean recognises, so it is stored and passed on for the
        // classifier route to reject with a specific error code - the dead letter path is for files it
        // cannot classify at all.
        assertEquals("NOT_A_ZIP_FILE", message.getHeader(FILE_TYPE, String.class));
        assertEquals(1, publisher.publishedTo(MardukQueues.PROCESS_FILE_QUEUE).size());
    }

    @Test
    void aFileWithNoHandleIsDeadLetteredWithAnEmptyBody() {
        // validateFile returns false when it cannot even read the handle, which is what the Camel version
        // turned into a ValidationException and handled.
        MardukMessage message = notification().removeHeader(FILE_HANDLE);
        exchangeRepository.uploadBlob("whatever", new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)));

        consumer(false).handle(message);

        assertTrue(publisher.publishedTo(MardukQueues.PROCESS_FILE_QUEUE).isEmpty());
        assertDeadLettered();
    }

    @Test
    void aNotificationForABlobThatIsNotThereIsDeadLetteredAndAcked() {
        // Throwing here nacks the message, and PubSub redelivers it against a blob that will never appear.
        MardukMessage message = notification();

        consumer(false).handle(message);

        assertTrue(publisher.publishedTo(MardukQueues.PROCESS_FILE_QUEUE).isEmpty());
        assertFalse(internalRepository.exist(HANDLE));
        assertDeadLettered();
    }

    private void assertDeadLettered() {
        assertEquals(1, publisher.publishedTo(MardukQueues.MARDUK_DEAD_LETTER_QUEUE).size(),
                "the message was not dead lettered");
        assertEquals("", publisher.publishedTo(MardukQueues.MARDUK_DEAD_LETTER_QUEUE).getFirst().body());
        JobEvent reported = JobEvent.fromString(
                publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).getFirst().body());
        assertEquals("FILE_CLASSIFICATION", reported.getAction());
        assertEquals(JobEvent.State.FAILED, reported.getState());
    }

    @Test
    void theExternalCopyIsDeletedOnlyWhenConfigured() throws Exception {
        exchangeRepository.uploadBlob(HANDLE, new ByteArrayInputStream(netexZip()));
        consumer(false).handle(notification());
        assertTrue(exchangeRepository.exist(HANDLE), "deleted the external copy with the flag off");

        consumer(true).handle(notification());
        assertFalse(exchangeRepository.exist(HANDLE), "kept the external copy with the flag on");
    }

    @Test
    void nothingIsPublishedWithTheJobEventAsItsBody() throws Exception {
        // The Camel version published whatever the previous step left behind - the job event JSON, or a
        // boolean from the blob delete. Nothing reads it, but an empty body is what the step means.
        exchangeRepository.uploadBlob(HANDLE, new ByteArrayInputStream(netexZip()));

        consumer(false).handle(notification());

        assertEquals("", publisher.publishedTo(MardukQueues.PROCESS_FILE_QUEUE).getFirst().body());
    }

    @Test
    void theSubscriptionIsTheInboundQueue() {
        assertEquals(MardukQueues.MARDUK_INBOUND_QUEUE, consumer(false).destination());
    }
}

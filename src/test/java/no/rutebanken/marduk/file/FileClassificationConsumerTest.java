package no.rutebanken.marduk.file;

import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.flexlines.FlexibleLinesImport;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import no.rutebanken.marduk.repository.InMemoryMardukBlobStoreRepository;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import no.rutebanken.marduk.validation.AntuValidation;
import no.rutebanken.marduk.validation.NetexValidationProfiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.FILE_TYPE;
import static no.rutebanken.marduk.Constants.IMPORT_TYPE;
import static no.rutebanken.marduk.Constants.IMPORT_TYPE_NETEX_FLEX;
import static no.rutebanken.marduk.Constants.JOB_ERROR_CODE;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_PREVALIDATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileClassificationConsumerTest {

    private static final String HANDLE = "inbound/received/rb_tst/netex.zip";
    private static final String ANTU_CONTAINER = "antu-exchange";

    private InMemoryMardukBlobStoreRepository internalRepository;
    private InMemoryMardukBlobStoreRepository antuRepository;
    private RecordingPubSubPublisher publisher;
    private ProviderRepository providerRepository;
    private List<String> flexImports;

    @BeforeEach
    void setUp() {
        antuRepository = new InMemoryMardukBlobStoreRepository(new ConcurrentHashMap<>());
        antuRepository.setContainerName(ANTU_CONTAINER);
        internalRepository = new InMemoryMardukBlobStoreRepository(new ConcurrentHashMap<>());
        internalRepository.setContainerName("marduk-internal");
        publisher = new RecordingPubSubPublisher();
        flexImports = new ArrayList<>();

        Provider provider = new Provider();
        ChouetteInfo chouetteInfo = new ChouetteInfo();
        chouetteInfo.setReferential("rb_tst");
        provider.setChouetteInfo(chouetteInfo);
        providerRepository = mock(ProviderRepository.class);
        when(providerRepository.getProvider(2L)).thenReturn(provider);
        when(providerRepository.getReferential(2L)).thenReturn("rb_tst");
    }

    private FileClassificationConsumer consumer(boolean chouettePreValidationEnabled) {
        MardukInternalBlobStoreService internalBlobStore =
                new MardukInternalBlobStoreService("marduk-internal", internalRepository);
        JobEventPublisher jobEvents = new JobEventPublisher(publisher);
        AntuValidation antuValidation = new AntuValidation(
                internalBlobStore, providerRepository, new NetexValidationProfiles(List.of(), List.of("OYM")),
                jobEvents, publisher, ANTU_CONTAINER);
        FlexibleLinesImport flexibleLinesImport = new FlexibleLinesImport(null, null, null, null, null) {
            @Override
            public void start(MardukMessage message) {
                flexImports.add(message.getHeader(FILE_HANDLE, String.class));
            }
        };
        return new FileClassificationConsumer(internalBlobStore, providerRepository, jobEvents, publisher,
                antuValidation, flexibleLinesImport, chouettePreValidationEnabled);
    }

    private static MardukMessage notification() {
        return new MardukMessage()
                .setHeader(PROVIDER_ID, 2L)
                .setHeader(CORRELATION_ID, "corr")
                .setHeader(FILE_NAME, "netex.zip")
                .setHeader(FILE_HANDLE, HANDLE);
    }

    private static byte[] zipContaining(String entryName, String content) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    private static byte[] netexZip() throws Exception {
        return zipContaining("line.xml",
                "<?xml version=\"1.0\"?><PublicationDelivery xmlns=\"http://www.netex.org.uk/netex\"/>");
    }

    private static byte[] gtfsZip() throws Exception {
        return zipContaining("agency.txt", "agency_id,agency_name\n1,Test\n");
    }

    private List<JobEvent> reportedEvents() {
        return publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).stream()
                .map(p -> JobEvent.fromString(p.body()))
                .toList();
    }

    private boolean reported(JobEvent.TimetableAction action, JobEvent.State state) {
        return reportedEvents().stream().anyMatch(
                e -> action.name().equals(e.getAction()) && state.equals(e.getState()));
    }

    @Test
    void aNetexFileIsPreValidatedAndImported() throws Exception {
        internalRepository.uploadBlob(HANDLE, new ByteArrayInputStream(netexZip()));

        consumer(true).handle(notification());

        assertTrue(reported(JobEvent.TimetableAction.FILE_TRANSFER, JobEvent.State.OK));
        assertTrue(reported(JobEvent.TimetableAction.FILE_CLASSIFICATION, JobEvent.State.STARTED));
        assertTrue(reported(JobEvent.TimetableAction.FILE_CLASSIFICATION, JobEvent.State.OK));
        assertTrue(reported(JobEvent.TimetableAction.PREVALIDATION, JobEvent.State.PENDING));
        assertEquals(1, publisher.publishedTo(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE).size());
        assertEquals(1, publisher.publishedTo(MardukQueues.CHOUETTE_IMPORT_QUEUE).size());
        assertTrue(antuRepository.exist(HANDLE) || internalRepository.exist(HANDLE));
    }

    @Test
    void theChouetteImportCarriesTheValidationRequestHeaders() throws Exception {
        // The routes were chained on one exchange, so the import message inherited whatever the
        // pre-validation step set. Preserved: the import consumer is not the only reader of these.
        internalRepository.uploadBlob(HANDLE, new ByteArrayInputStream(netexZip()));

        consumer(true).handle(notification());

        assertEquals(VALIDATION_STAGE_PREVALIDATION,
                publisher.publishedTo(MardukQueues.CHOUETTE_IMPORT_QUEUE).getFirst()
                        .attributes().get(VALIDATION_STAGE_HEADER));
    }

    @Test
    void theChouetteImportIsSkippedForNetexWhenPreValidationIsOff() throws Exception {
        internalRepository.uploadBlob(HANDLE, new ByteArrayInputStream(netexZip()));

        consumer(false).handle(notification());

        assertEquals(1, publisher.publishedTo(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE).size());
        assertTrue(publisher.publishedTo(MardukQueues.CHOUETTE_IMPORT_QUEUE).isEmpty(),
                "a NeTEx import must wait for antu's verdict when pre-validation is off");
    }

    @Test
    void aGtfsFileIsImportedWithoutAntuEvenWhenPreValidationIsOff() throws Exception {
        internalRepository.uploadBlob(HANDLE, new ByteArrayInputStream(gtfsZip()));

        consumer(false).handle(notification());

        assertTrue(publisher.publishedTo(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE).isEmpty(),
                "antu only validates NeTEx");
        assertEquals(1, publisher.publishedTo(MardukQueues.CHOUETTE_IMPORT_QUEUE).size());
    }

    @Test
    void aFlexImportGoesToTheFlexibleLinesImportAndNowhereElse() throws Exception {
        internalRepository.uploadBlob(HANDLE, new ByteArrayInputStream(netexZip()));

        consumer(true).handle(notification().setHeader(IMPORT_TYPE, IMPORT_TYPE_NETEX_FLEX));

        assertEquals(List.of(HANDLE), flexImports);
        assertTrue(publisher.publishedTo(MardukQueues.CHOUETTE_IMPORT_QUEUE).isEmpty());
        assertTrue(publisher.publishedTo(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE).isEmpty());
        assertTrue(reported(JobEvent.TimetableAction.FILE_CLASSIFICATION, JobEvent.State.OK));
    }

    @Test
    void aFileWithoutAZipExtensionFailsWithItsOwnErrorCode() {
        String handle = "inbound/received/rb_tst/netex.rar";
        internalRepository.uploadBlob(handle, new ByteArrayInputStream("whatever".getBytes(StandardCharsets.UTF_8)));

        MardukMessage message = notification().setHeader(FILE_HANDLE, handle);
        consumer(true).handle(message);

        assertEquals(JobEvent.JOB_ERROR_FILE_UNKNOWN_FILE_EXTENSION, message.getHeader(JOB_ERROR_CODE, String.class));
        assertTrue(reported(JobEvent.TimetableAction.FILE_CLASSIFICATION, JobEvent.State.FAILED));
        assertTrue(publisher.publishedTo(MardukQueues.CHOUETTE_IMPORT_QUEUE).isEmpty());
    }

    @Test
    void aFileThatIsNotAZipFailsWithItsOwnErrorCode() {
        internalRepository.uploadBlob(HANDLE, new ByteArrayInputStream("not a zip".getBytes(StandardCharsets.UTF_8)));

        MardukMessage message = notification();
        consumer(true).handle(message);

        assertEquals(JobEvent.JOB_ERROR_FILE_NOT_A_ZIP_FILE, message.getHeader(JOB_ERROR_CODE, String.class));
        assertTrue(reported(JobEvent.TimetableAction.FILE_CLASSIFICATION, JobEvent.State.FAILED));
    }

    @Test
    void aZipWithSubdirectoriesFailsWithItsOwnErrorCode() throws Exception {
        internalRepository.uploadBlob(HANDLE,
                new ByteArrayInputStream(zipContaining("sub/line.xml", "<x/>")));

        MardukMessage message = notification();
        consumer(true).handle(message);

        assertEquals(JobEvent.JOB_ERROR_FILE_ZIP_CONTAINS_SUB_DIRECTORIES,
                message.getHeader(JOB_ERROR_CODE, String.class));
        assertTrue(reported(JobEvent.TimetableAction.FILE_CLASSIFICATION, JobEvent.State.FAILED));
    }

    @Test
    void aZipThatIsNeitherNetexNorGtfsFailsWithItsOwnErrorCode() throws Exception {
        internalRepository.uploadBlob(HANDLE, new ByteArrayInputStream(zipContaining("readme.txt", "hello")));

        MardukMessage message = notification();
        consumer(true).handle(message);

        assertEquals(JobEvent.JOB_ERROR_UNKNOWN_FILE_TYPE, message.getHeader(JOB_ERROR_CODE, String.class));
        assertTrue(reported(JobEvent.TimetableAction.FILE_CLASSIFICATION, JobEvent.State.FAILED));
    }

    @Test
    void anUnparseableXmlFileFailsWithItsOwnErrorCode() throws Exception {
        internalRepository.uploadBlob(HANDLE, new ByteArrayInputStream(zipContaining("line.xml", "<not closed")));

        MardukMessage message = notification();
        consumer(true).handle(message);

        assertEquals(JobEvent.JOB_ERROR_INVALID_XML_CONTENT, message.getHeader(JOB_ERROR_CODE, String.class));
        assertTrue(reported(JobEvent.TimetableAction.FILE_CLASSIFICATION, JobEvent.State.FAILED));
    }

    @Test
    void anXmlFileWithTheWrongEncodingFailsWithItsOwnErrorCode() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("line.xml"));
            // A latin-1 byte in a file declaring UTF-8, before the root element, so the parser hits it while
            // looking for the first element rather than after deciding the file is not NeTEx.
            zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?><!-- \u00e6 -->"
                    + "<PublicationDelivery xmlns=\"http://www.netex.org.uk/netex\"/>")
                    .getBytes(StandardCharsets.ISO_8859_1));
            zip.closeEntry();
        }
        internalRepository.uploadBlob(HANDLE, new ByteArrayInputStream(out.toByteArray()));

        MardukMessage message = notification();
        consumer(true).handle(message);

        assertEquals(JobEvent.JOB_ERROR_INVALID_XML_ENCODING, message.getHeader(JOB_ERROR_CODE, String.class));
        assertTrue(reported(JobEvent.TimetableAction.FILE_CLASSIFICATION, JobEvent.State.FAILED));
    }

    @Test
    void aFileWithAnUnusableNameIsRenamedAndPutBackOnTheQueue() throws Exception {
        String handle = "inbound/received/rb_tst/net\tex.zip";
        internalRepository.uploadBlob(handle, new ByteArrayInputStream(netexZip()));

        MardukMessage message = notification()
                .setHeader(FILE_HANDLE, handle)
                .setHeader(FILE_NAME, "net\tex.zip");
        consumer(true).handle(message);

        assertEquals("netex.zip", message.getHeader(FILE_NAME, String.class));
        assertEquals("inbound/received/rb_tst/netex.zip", message.getHeader(FILE_HANDLE, String.class));
        assertTrue(internalRepository.exist("inbound/received/rb_tst/netex.zip"),
                "the renamed file was not stored, so the next round would find nothing");
        assertEquals(1, publisher.publishedTo(MardukQueues.PROCESS_FILE_QUEUE).size());
        assertTrue(publisher.publishedTo(MardukQueues.CHOUETTE_IMPORT_QUEUE).isEmpty(),
                "the file is classified again after the rename, not imported straight away");
    }

    @Test
    void theRepublishedFileIsNotSentAsTheMessageBody() throws Exception {
        // The bytes belong in the blob store. A megabyte-sized body would be rejected by PubSub, and the
        // Camel version cleared it in the upload route.
        String handle = "inbound/received/rb_tst/net\tex.zip";
        internalRepository.uploadBlob(handle, new ByteArrayInputStream(netexZip()));

        consumer(true).handle(notification()
                .setHeader(FILE_HANDLE, handle)
                .setHeader(FILE_NAME, "net\tex.zip"));

        assertEquals("", publisher.publishedTo(MardukQueues.PROCESS_FILE_QUEUE).getFirst().body());
    }

    @Test
    void anUnclassifiableFileIsDeadLetteredWithNoErrorCode() {
        // A truncated archive: the magic bytes say zip, opening it does not. The classifier gives up rather
        // than returning a type, which the Camel version turned into a ValidationException.
        byte[] truncated = new byte[]{0x50, 0x4b, 0x03, 0x04, 0x00, 0x01, 0x02};
        internalRepository.uploadBlob(HANDLE, new ByteArrayInputStream(truncated));

        MardukMessage message = notification();
        consumer(true).handle(message);

        assertEquals(1, publisher.publishedTo(MardukQueues.MARDUK_DEAD_LETTER_QUEUE).size());
        assertEquals("", publisher.publishedTo(MardukQueues.MARDUK_DEAD_LETTER_QUEUE).getFirst().body());
        assertTrue(reported(JobEvent.TimetableAction.FILE_CLASSIFICATION, JobEvent.State.FAILED));
        assertFalse(reportedEvents().stream().anyMatch(e -> e.getErrorCode() != null),
                "the give-up path reported an error code the operator has no way to interpret");
    }

    @Test
    void theSubscriptionIsTheProcessFileQueue() {
        assertEquals(MardukQueues.PROCESS_FILE_QUEUE, consumer(true).destination());
    }
}

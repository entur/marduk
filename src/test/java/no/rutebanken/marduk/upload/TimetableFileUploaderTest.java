package no.rutebanken.marduk.upload;

import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import no.rutebanken.marduk.repository.InMemoryMardukBlobStoreRepository;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_INBOUND;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.IMPORT_TYPE;
import static no.rutebanken.marduk.Constants.IMPORT_TYPE_NETEX_FLEX;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.USERNAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TimetableFileUploaderTest {

    private static final String HANDLE = BLOBSTORE_PATH_INBOUND + "rut/netex.zip";

    private InMemoryMardukBlobStoreRepository internalRepository;
    private RecordingPubSubPublisher publisher;
    private ProviderRepository providerRepository;
    private DuplicateFileFilter duplicateFileFilter;

    /** Rejects everything, to drive the duplicate branch without depending on a previous upload. */
    private static final class AlwaysSeen implements no.rutebanken.marduk.repository.IdempotentRepository {
        @Override
        public boolean add(String key) {
            return false;
        }

        @Override
        public boolean contains(String key) {
            return true;
        }

        @Override
        public boolean remove(String key) {
            return false;
        }

        @Override
        public void clear() {
        }
    }

    private static final class NeverSeen implements no.rutebanken.marduk.repository.IdempotentRepository {
        private final Set<String> removed = new HashSet<>();

        @Override
        public boolean add(String key) {
            return true;
        }

        @Override
        public boolean contains(String key) {
            return false;
        }

        @Override
        public boolean remove(String key) {
            return removed.add(key);
        }

        @Override
        public void clear() {
        }

        Set<String> removed() {
            return removed;
        }
    }

    @BeforeEach
    void setUp() {
        internalRepository = new InMemoryMardukBlobStoreRepository(new ConcurrentHashMap<>());
        internalRepository.setContainerName("marduk-internal");
        publisher = new RecordingPubSubPublisher();
        providerRepository = mock(ProviderRepository.class);
        when(providerRepository.getProvider(2L)).thenReturn(provider(true));
        duplicateFileFilter = new DuplicateFileFilter(new NeverSeen(), new JobEventPublisher(publisher));
    }

    private static Provider provider(boolean autoImport) {
        Provider provider = new Provider();
        provider.setId(2L);
        ChouetteInfo info = new ChouetteInfo();
        info.setReferential("rut");
        info.setEnableAutoImport(autoImport);
        provider.setChouetteInfo(info);
        return provider;
    }

    private TimetableFileUploader uploader() {
        return new TimetableFileUploader(
                new MardukInternalBlobStoreService("marduk-internal", internalRepository),
                providerRepository, duplicateFileFilter, new JobEventPublisher(publisher), publisher);
    }

    private static MultipartFile file(String content) {
        return file("netex.zip", content);
    }

    private static MultipartFile file(String name, String content) {
        return new MockMultipartFile(name, name, "application/zip", content.getBytes(StandardCharsets.UTF_8));
    }

    /** Fails on the way in, the way a part whose stream is already gone does. */
    private static MultipartFile unreadableFile(String name) {
        return new MockMultipartFile(name, name, "application/zip", "x".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public InputStream getInputStream() throws IOException {
                throw new IOException("cannot read " + name);
            }
        };
    }

    private static TimetableFileUploader.Upload upload(String importType) {
        return new TimetableFileUploader.Upload("rut", 2L, "corr", "someone", importType, true, false);
    }

    private List<JobEvent> reportedEvents() {
        return publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).stream()
                .map(p -> JobEvent.fromString(p.body()))
                .toList();
    }

    @Test
    void theFileIsStoredUnderTheProvidersInboundPath() {
        uploader().upload(file("zip bytes"), upload(null));

        assertTrue(internalRepository.exist(HANDLE));
    }

    @Test
    void theImportPipelineIsStartedForTheStoredFile() {
        uploader().upload(file("zip bytes"), upload(null));

        var queued = publisher.publishedTo(MardukQueues.PROCESS_FILE_QUEUE);
        assertEquals(1, queued.size());
        assertEquals(HANDLE, queued.getFirst().attributes().get(FILE_HANDLE));
        assertEquals("netex.zip", queued.getFirst().attributes().get(FILE_NAME));
        assertEquals("rut", queued.getFirst().attributes().get(CHOUETTE_REFERENTIAL));
        assertEquals("corr", queued.getFirst().attributes().get(CORRELATION_ID));
        assertEquals("someone", queued.getFirst().attributes().get(USERNAME));
    }

    @Test
    void aFlexUploadIsMarkedAsSuchOnTheQueue() {
        uploader().upload(file("zip bytes"), upload(IMPORT_TYPE_NETEX_FLEX));

        assertEquals(IMPORT_TYPE_NETEX_FLEX,
                publisher.publishedTo(MardukQueues.PROCESS_FILE_QUEUE).getFirst().attributes().get(IMPORT_TYPE));
    }

    @Test
    void theTransferIsReportedAsStartedBeforeAnythingElseHappens() {
        // An upload that then fails still has to show up in nabu as having been attempted.
        uploader().upload(file("zip bytes"), upload(null));

        JobEvent first = reportedEvents().getFirst();
        assertEquals("FILE_TRANSFER", first.getAction());
        assertEquals(JobEvent.State.STARTED, first.getState());
    }

    @Test
    void aProviderWithAutoImportOffKeepsTheFileButCancelsTheTransfer() {
        when(providerRepository.getProvider(2L)).thenReturn(provider(false));

        uploader().upload(file("zip bytes"), upload(null));

        assertTrue(internalRepository.exist(HANDLE), "the file should still be stored");
        assertTrue(publisher.publishedTo(MardukQueues.PROCESS_FILE_QUEUE).isEmpty());
        assertEquals(JobEvent.State.CANCELLED, reportedEvents().getLast().getState());
    }

    @Test
    void aDuplicateIsNotStoredAndStartsNothing() {
        duplicateFileFilter = new DuplicateFileFilter(new AlwaysSeen(), new JobEventPublisher(publisher));

        uploader().upload(file("zip bytes"), upload(null));

        assertFalse(internalRepository.exist(HANDLE), "a duplicate was stored anyway");
        assertTrue(publisher.publishedTo(MardukQueues.PROCESS_FILE_QUEUE).isEmpty());
        assertEquals(JobEvent.JOB_ERROR_DUPLICATE_FILE, reportedEvents().getLast().getErrorCode());
    }

    @Test
    void aFailedUploadReportsTheFailureAndReleasesTheDuplicateClaim() {
        // Without the release, that file could never be uploaded again.
        NeverSeen keys = new NeverSeen();
        duplicateFileFilter = new DuplicateFileFilter(keys, new JobEventPublisher(publisher));
        when(providerRepository.getProvider(2L)).thenReturn(null);

        assertThrows(MardukException.class, () -> uploader().upload(file("zip bytes"), upload(null)));

        assertEquals(1, keys.removed().size(), "the claim was not released after the failure");
        assertEquals(JobEvent.State.FAILED, reportedEvents().getLast().getState());
    }

    @Test
    void anEmptyUploadIsRejectedBeforeAnythingIsReported() {
        MultipartFile empty = new MockMultipartFile("netex.zip", "netex.zip", "application/zip", new byte[0]);

        assertThrows(IllegalArgumentException.class, () -> uploader().upload(empty, upload(null)));

        assertTrue(publisher.published().isEmpty());
    }

    @Test
    void oneFileThatFailsDoesNotStopTheOthers() {
        uploader().uploadAll(
                List.of(file("first.zip", "a"), unreadableFile("bad.zip"), file("third.zip", "c")), upload(null));

        assertTrue(internalRepository.exist(BLOBSTORE_PATH_INBOUND + "rut/first.zip"));
        assertFalse(internalRepository.exist(BLOBSTORE_PATH_INBOUND + "rut/bad.zip"));
        assertTrue(internalRepository.exist(BLOBSTORE_PATH_INBOUND + "rut/third.zip"),
                "the file after the failing one was never uploaded");
        assertEquals(2, publisher.publishedTo(MardukQueues.PROCESS_FILE_QUEUE).size());
    }

    @Test
    void eachFileOfAMultiFileUploadIsReportedOnItsOwn() {
        uploader().uploadAll(List.of(file("first.zip", "a"), unreadableFile("bad.zip")), upload(null));

        List<JobEvent> reported = reportedEvents();
        assertEquals(1, reported.stream().filter(event -> event.getState() == JobEvent.State.FAILED).count(),
                "the failing file was not reported as failed");
        assertEquals("bad.zip", reported.stream()
                .filter(event -> event.getState() == JobEvent.State.FAILED).findFirst().orElseThrow()
                .getName());
    }

    @Test
    void theStoredBytesAreTheUploadedBytes() throws IOException {
        uploader().upload(file("exactly these bytes"), upload(null));

        try (InputStream stored = internalRepository.getBlob(HANDLE)) {
            assertEquals("exactly these bytes", new String(stored.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}

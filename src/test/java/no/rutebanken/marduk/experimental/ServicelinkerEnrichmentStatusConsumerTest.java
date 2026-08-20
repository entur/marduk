package no.rutebanken.marduk.experimental;

import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import no.rutebanken.marduk.repository.InMemoryMardukBlobStoreRepository;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.experimental.ExperimentalImportHelpers;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.FILTERING_FILE_CREATED_TIMESTAMP;
import static no.rutebanken.marduk.Constants.LINKED_NETEX_FILE_PATH_HEADER;
import static no.rutebanken.marduk.Constants.LINKING_ERROR_CODE_HEADER;
import static no.rutebanken.marduk.Constants.LINKING_NETEX_FILE_STATUS_FAILED;
import static no.rutebanken.marduk.Constants.LINKING_NETEX_FILE_STATUS_HEADER;
import static no.rutebanken.marduk.Constants.LINKING_NETEX_FILE_STATUS_STARTED;
import static no.rutebanken.marduk.Constants.LINKING_NETEX_FILE_STATUS_SUCCEEDED;
import static no.rutebanken.marduk.Constants.LINKING_STATUS_EVENT_TIME_HEADER;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServicelinkerEnrichmentStatusConsumerTest {

    private static final String INTERNAL = "marduk-internal";
    private static final String EXCHANGE = "marduk-exchange";
    private static final String SERVICELINKER = "servicelinker-exchange";
    private static final String ORIGINAL = "chouette/netex-before-validation/tst-export.zip";
    private static final String ENRICHED = "servicelinker/tst/corr/tst-aggregated-netex.zip";

    private final Map<String, Map<String, byte[]>> buckets = new ConcurrentHashMap<>();
    private final RecordingPubSubPublisher publisher = new RecordingPubSubPublisher();
    private final ProviderRepository providerRepository = mock(ProviderRepository.class);

    private ServicelinkerEnrichmentStatusConsumer consumer;

    @BeforeEach
    void setUp() {
        Provider provider = new Provider();
        provider.setId(2L);
        ChouetteInfo info = new ChouetteInfo();
        info.setReferential("tst");
        info.setEnableExperimentalImport(true);
        provider.setChouetteInfo(info);
        when(providerRepository.getProviders()).thenReturn(List.of(provider));
        when(providerRepository.getProviderId("tst")).thenReturn(2L);

        ExperimentalImportHelpers helpers = new ExperimentalImportHelpers(true, providerRepository);
        JobEventPublisher jobEvents = new JobEventPublisher(publisher);
        ExperimentalImportPath path = new ExperimentalImportPath(
                helpers,
                new MardukInternalBlobStoreService(INTERNAL, new InMemoryMardukBlobStoreRepository(buckets)),
                publisher, jobEvents, true, EXCHANGE);
        consumer = new ServicelinkerEnrichmentStatusConsumer(
                providerRepository, path,
                new MardukInternalBlobStoreService(INTERNAL, new InMemoryMardukBlobStoreRepository(buckets)),
                jobEvents, SERVICELINKER);
    }

    private MardukMessage status(String linkingStatus) {
        buckets.computeIfAbsent(INTERNAL, key -> new ConcurrentHashMap<>())
                .put(ORIGINAL, "zip".getBytes(StandardCharsets.UTF_8));
        buckets.computeIfAbsent(SERVICELINKER, key -> new ConcurrentHashMap<>())
                .put(ENRICHED, "enriched".getBytes(StandardCharsets.UTF_8));
        return new MardukMessage()
                .setHeader(CORRELATION_ID, "corr")
                .setHeader(DATASET_REFERENTIAL, "tst")
                .setHeader(FILE_NAME, "netex.zip")
                .setHeader(FILE_HANDLE, ORIGINAL)
                .setHeader(FILTERING_FILE_CREATED_TIMESTAMP, "2026-03-27T12:00:00")
                .setHeader(LINKED_NETEX_FILE_PATH_HEADER, ENRICHED)
                .setHeader(LINKING_NETEX_FILE_STATUS_HEADER, linkingStatus);
    }

    private byte[] blob(String container, String name) {
        return buckets.getOrDefault(container, Map.of()).get(name);
    }

    private JobEvent linkingEvent() {
        return JobEvent.fromString(publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).getFirst().body());
    }

    private String handleSentToAshur() {
        return publisher.publishedTo(MardukQueues.FILTER_NETEX_FILE_QUEUE).getFirst().attributes().get(FILE_HANDLE);
    }

    @Test
    void theJobIsResolvedFromTheDatasetReferentialTheStatusCarries() {
        MardukMessage message = status(LINKING_NETEX_FILE_STATUS_STARTED);

        consumer.handle(message);

        assertEquals(2L, message.getHeader(PROVIDER_ID, Long.class));
        assertEquals("tst", message.getHeader(CHOUETTE_REFERENTIAL, String.class));
    }

    @Test
    void anEnrichedDatasetIsKeptAtItsOwnPathAndSentOnToAshur() {
        consumer.handle(status(LINKING_NETEX_FILE_STATUS_SUCCEEDED));

        assertNotNull(blob(INTERNAL, ENRICHED), "the enriched file is copied into marduk's own bucket");
        assertEquals("zip", new String(blob(INTERNAL, ORIGINAL), StandardCharsets.UTF_8),
                "the file that was sent for enrichment is left untouched");
        assertEquals(JobEvent.TimetableAction.LINKING.name(), linkingEvent().getAction());
        assertEquals(JobEvent.State.OK, linkingEvent().getState());
        assertEquals(ENRICHED, handleSentToAshur(), "Ashur must filter the enriched file, not the original");
    }

    @Test
    void aStartedEnrichmentIsReportedAndTheImportWaits() {
        consumer.handle(status(LINKING_NETEX_FILE_STATUS_STARTED));

        assertEquals(JobEvent.State.STARTED, linkingEvent().getState());
        assertNull(blob(INTERNAL, ENRICHED));
        assertTrue(publisher.publishedTo(MardukQueues.FILTER_NETEX_FILE_QUEUE).isEmpty());
    }

    @Test
    void aFailedEnrichmentIsReportedAndTheImportCarriesOnWithTheOriginalFile() {
        // A dataset without generated service links is still importable, so linking failure degrades
        // rather than stopping the import.
        consumer.handle(status(LINKING_NETEX_FILE_STATUS_FAILED)
                .setHeader(LINKING_ERROR_CODE_HEADER, "OSRM timeout"));

        assertEquals(JobEvent.State.FAILED, linkingEvent().getState());
        assertEquals("OSRM timeout", linkingEvent().getErrorCode());
        assertNull(blob(INTERNAL, ENRICHED));
        assertEquals(ORIGINAL, handleSentToAshur());
    }

    @Test
    void anUnknownLinkingStatusStopsTheImportRatherThanBeingRetried() {
        consumer.handle(status("NO_SUCH_STATUS"));

        assertTrue(publisher.published().isEmpty());
    }

    @Test
    void theEventTimeServicelinkerStampedIsWhatIsReported() {
        // Keeps STARTED ordered before SUCCESS in nabu even when PubSub delivers the two out of order.
        Instant emitted = Instant.parse("2026-06-03T10:15:30Z");

        consumer.handle(status(LINKING_NETEX_FILE_STATUS_STARTED)
                .setHeader(LINKING_STATUS_EVENT_TIME_HEADER, emitted.toString()));

        assertEquals(emitted, linkingEvent().getEventTime());
    }

    @Test
    void anUnparseableEventTimeFallsBackToTheTimeTheStatusWasHandled() {
        Instant before = Instant.now();

        consumer.handle(status(LINKING_NETEX_FILE_STATUS_STARTED)
                .setHeader(LINKING_STATUS_EVENT_TIME_HEADER, "not-a-timestamp"));

        Instant reported = linkingEvent().getEventTime();
        assertTrue(!reported.isBefore(before) && !reported.isAfter(Instant.now()), "was " + reported);
    }

    @Test
    void aMissingEventTimeFallsBackToTheTimeTheStatusWasHandled() {
        Instant before = Instant.now();

        consumer.handle(status(LINKING_NETEX_FILE_STATUS_STARTED));

        Instant reported = linkingEvent().getEventTime();
        assertTrue(!reported.isBefore(before) && !reported.isAfter(Instant.now()), "was " + reported);
    }
}

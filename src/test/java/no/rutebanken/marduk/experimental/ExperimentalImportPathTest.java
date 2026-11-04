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
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.FILTERING_FILE_CREATED_TIMESTAMP;
import static no.rutebanken.marduk.Constants.FILTERING_NETEX_SOURCE_HEADER;
import static no.rutebanken.marduk.Constants.FILTERING_NETEX_SOURCE_MARDUK;
import static no.rutebanken.marduk.Constants.FILTERING_PROFILE_HEADER;
import static no.rutebanken.marduk.Constants.FILTERING_PROFILE_STANDARD_IMPORT;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.SERVICE_LINK_MODES_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExperimentalImportPathTest {

    private static final String INTERNAL = "marduk-internal";
    private static final String EXCHANGE = "marduk-exchange";
    private static final String DATASET = "inbound/received/tst/netex.zip";

    private final Map<String, Map<String, byte[]>> buckets = new ConcurrentHashMap<>();
    private final RecordingPubSubPublisher publisher = new RecordingPubSubPublisher();
    private final ProviderRepository providerRepository = mock(ProviderRepository.class);

    private ExperimentalImportPath path(boolean linkingEnabled) {
        InMemoryMardukBlobStoreRepository repository = new InMemoryMardukBlobStoreRepository(buckets);
        return new ExperimentalImportPath(
                new ExperimentalImportHelpers(true, providerRepository),
                new MardukInternalBlobStoreService(INTERNAL, repository),
                publisher,
                new JobEventPublisher(publisher),
                linkingEnabled,
                EXCHANGE);
    }

    private void provider(Set<String> serviceLinkModes) {
        Provider provider = new Provider();
        provider.setId(2L);
        ChouetteInfo info = new ChouetteInfo();
        info.setReferential("tst");
        info.setEnableExperimentalImport(true);
        info.setGenerateMissingServiceLinksForModes(serviceLinkModes);
        provider.setChouetteInfo(info);
        when(providerRepository.getProviders()).thenReturn(List.of(provider));
    }

    private MardukMessage prevalidated() {
        buckets.computeIfAbsent(INTERNAL, key -> new ConcurrentHashMap<>())
                .put(DATASET, "zip".getBytes(StandardCharsets.UTF_8));
        return new MardukMessage()
                .setHeader(PROVIDER_ID, 2L)
                .setHeader(CORRELATION_ID, "corr")
                .setHeader(DATASET_REFERENTIAL, "tst")
                .setHeader(CHOUETTE_REFERENTIAL, "tst")
                .setHeader(FILE_NAME, "netex.zip")
                .setHeader(FILE_HANDLE, DATASET)
                .setHeader(FILTERING_FILE_CREATED_TIMESTAMP, "2026-03-27T12:00:00");
    }

    private byte[] blob(String container, String name) {
        return buckets.getOrDefault(container, Map.of()).get(name);
    }

    private JobEvent reported(int index) {
        return JobEvent.fromString(publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).get(index).body());
    }

    @Test
    void aDatasetHandedToAshurIsCopiedToTheExchangeBucketAndKeptForTheBlockExport() {
        provider(null);

        path(false).filterAfterPreValidation(prevalidated());

        assertNotNull(blob(EXCHANGE, "outbound/netex/rb_tst/corr/rb_tst-aggregated-netex.zip"),
                "Ashur reads the dataset from the exchange bucket");
        assertNotNull(blob(INTERNAL, "filtered-netex/rb_tst/block-export-input/corr/rb_tst-aggregated-netex.zip"),
                "the block export filters the same input again later");
        assertEquals(1, publisher.publishedTo(MardukQueues.FILTER_NETEX_FILE_QUEUE).size());
    }

    @Test
    void theFilteringRequestNamesTheStandardImportProfileAndMardukAsTheSource() {
        provider(null);

        path(false).filterAfterPreValidation(prevalidated());

        Map<String, String> attributes =
                publisher.publishedTo(MardukQueues.FILTER_NETEX_FILE_QUEUE).getFirst().attributes();
        assertEquals(FILTERING_PROFILE_STANDARD_IMPORT, attributes.get(FILTERING_PROFILE_HEADER));
        assertEquals(FILTERING_NETEX_SOURCE_MARDUK, attributes.get(FILTERING_NETEX_SOURCE_HEADER));
        assertEquals("rb_tst", attributes.get(DATASET_REFERENTIAL));
        assertEquals("rb_tst", attributes.get(CHOUETTE_REFERENTIAL));
    }

    @Test
    void theRbPrefixIsAppliedOnlyOnceTheDatasetLeavesForAshur() {
        // The links to antu's pre-validation reports are built from the unprefixed referential, so the
        // pre-validation status has to be reported before this step runs.
        provider(null);
        MardukMessage message = prevalidated();

        path(false).filterAfterPreValidation(message);

        assertEquals("rb_tst", message.getHeader(DATASET_REFERENTIAL, String.class));
        assertEquals("rb_tst", message.getHeader(CHOUETTE_REFERENTIAL, String.class));
    }

    @Test
    void aPendingFilteringIsReported() {
        provider(null);

        path(false).filterAfterPreValidation(prevalidated());

        assertEquals(JobEvent.TimetableAction.FILTERING.name(), reported(0).getAction());
        assertEquals(JobEvent.State.PENDING, reported(0).getState());
    }

    @Test
    void aDatasetWithNoCreatedTimestampIsNotSentToAshurAtAll() {
        // Ashur names its output after the timestamp, so without one the import cannot be followed through.
        provider(null);
        MardukMessage message = prevalidated().removeHeader(FILTERING_FILE_CREATED_TIMESTAMP);

        path(false).filterAfterPreValidation(message);

        assertTrue(publisher.published().isEmpty(), "nothing may be published without a created timestamp");
        assertEquals("tst", message.getHeader(DATASET_REFERENTIAL, String.class), "the rb_ prefix must not go on");
    }

    @Test
    void servicelinkerIsSkippedWhenLinkingIsDisabled() {
        provider(null);

        path(false).enrichThenFilter(prevalidated());

        assertTrue(publisher.publishedTo(MardukQueues.SERVICELINKER_INBOUND_QUEUE).isEmpty());
        assertEquals(1, publisher.publishedTo(MardukQueues.FILTER_NETEX_FILE_QUEUE).size(),
                "the import goes straight on to Ashur");
    }

    @Test
    void servicelinkerIsSkippedWhenTheProviderWantsNoServiceLinks() {
        provider(Set.of());

        path(true).enrichThenFilter(prevalidated());

        assertTrue(publisher.publishedTo(MardukQueues.SERVICELINKER_INBOUND_QUEUE).isEmpty());
        assertEquals(1, publisher.publishedTo(MardukQueues.FILTER_NETEX_FILE_QUEUE).size());
    }

    @Test
    void anEnrichmentRequestCopiesTheDatasetOutAndWaitsForTheCallback() {
        provider(null);

        path(true).enrichThenFilter(prevalidated());

        assertNotNull(blob(EXCHANGE, "servicelinker/tst/corr/tst-aggregated-netex.zip"));
        assertEquals(1, publisher.publishedTo(MardukQueues.SERVICELINKER_INBOUND_QUEUE).size());
        assertTrue(publisher.publishedTo(MardukQueues.FILTER_NETEX_FILE_QUEUE).isEmpty(),
                "Ashur is triggered from the servicelinker callback, not here");
        assertEquals(JobEvent.TimetableAction.LINKING.name(), reported(0).getAction());
        assertEquals(JobEvent.State.PENDING, reported(0).getState());
    }

    @Test
    void configuredServiceLinkModesRestrictWhatServicelinkerGenerates() {
        provider(Set.of("bus"));

        path(true).enrichThenFilter(prevalidated());

        assertEquals("bus", publisher.publishedTo(MardukQueues.SERVICELINKER_INBOUND_QUEUE)
                .getFirst().attributes().get(SERVICE_LINK_MODES_HEADER));
    }

    @Test
    void unconfiguredServiceLinkModesLetServicelinkerGenerateForAllOfThem() {
        provider(null);

        path(true).enrichThenFilter(prevalidated());

        assertNull(publisher.publishedTo(MardukQueues.SERVICELINKER_INBOUND_QUEUE)
                .getFirst().attributes().get(SERVICE_LINK_MODES_HEADER));
    }
}

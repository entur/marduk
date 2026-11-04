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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILTERING_NETEX_SOURCE_HEADER;
import static no.rutebanken.marduk.Constants.FILTERING_NETEX_SOURCE_MARDUK;
import static no.rutebanken.marduk.Constants.FILTERING_PROFILE_HEADER;
import static no.rutebanken.marduk.Constants.FILTERING_PROFILE_INCLUDE_BLOCKS_AND_RESTRICTED_JOURNEYS;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExportNetexBlocksConsumerTest {

    private static final String INTERNAL = "marduk-internal";
    private static final String EXCHANGE = "marduk-exchange";
    private static final String PRE_FILTERING =
            "filtered-netex/rb_tst/block-export-input/corr/rb_tst-aggregated-netex.zip";
    private static final String FOR_ASHUR = "outbound/netex/rb_tst/corr/rb_tst-netex-with-blocks.zip";

    private final Map<String, Map<String, byte[]>> buckets = new ConcurrentHashMap<>();
    private final RecordingPubSubPublisher publisher = new RecordingPubSubPublisher();
    private final ProviderRepository providerRepository = mock(ProviderRepository.class);

    private ExportNetexBlocksConsumer consumer(boolean blocksExportEnabled) {
        Provider provider = new Provider();
        provider.setId(2L);
        ChouetteInfo info = new ChouetteInfo();
        info.setReferential("tst");
        info.setEnableBlocksExport(blocksExportEnabled);
        provider.setChouetteInfo(info);
        when(providerRepository.getProvider(2L)).thenReturn(provider);

        return new ExportNetexBlocksConsumer(
                providerRepository,
                new ExperimentalImportHelpers(true, providerRepository),
                new MardukInternalBlobStoreService(INTERNAL, new InMemoryMardukBlobStoreRepository(buckets)),
                publisher,
                new JobEventPublisher(publisher),
                EXCHANGE);
    }

    private MardukMessage request() {
        buckets.computeIfAbsent(INTERNAL, key -> new ConcurrentHashMap<>())
                .put(PRE_FILTERING, "zip".getBytes(StandardCharsets.UTF_8));
        return new MardukMessage()
                .setHeader(PROVIDER_ID, 2L)
                .setHeader(CORRELATION_ID, "corr")
                .setHeader(DATASET_REFERENTIAL, "rb_tst")
                .setHeader(CHOUETTE_REFERENTIAL, "rb_tst");
    }

    private byte[] blob(String container, String name) {
        return buckets.getOrDefault(container, Map.of()).get(name);
    }

    @Test
    void theInputToTheBlockExportIsTheFileSavedBeforeTheStandardFiltering() {
        // The standard filter strips blocks, so filtering its output again could never produce them.
        consumer(true).handle(request());

        assertNotNull(blob(EXCHANGE, FOR_ASHUR));
        assertEquals("zip", new String(blob(EXCHANGE, FOR_ASHUR), StandardCharsets.UTF_8));
    }

    @Test
    void theFilteringRequestAsksForBlocksAndRestrictedJourneys() {
        consumer(true).handle(request());

        Map<String, String> attributes =
                publisher.publishedTo(MardukQueues.FILTER_NETEX_FILE_QUEUE).getFirst().attributes();
        assertEquals(FILTERING_PROFILE_INCLUDE_BLOCKS_AND_RESTRICTED_JOURNEYS, attributes.get(FILTERING_PROFILE_HEADER));
        assertEquals(FILTERING_NETEX_SOURCE_MARDUK, attributes.get(FILTERING_NETEX_SOURCE_HEADER));
    }

    @Test
    void aPendingBlockExportIsReported() {
        consumer(true).handle(request());

        JobEvent reported = JobEvent.fromString(
                publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).getFirst().body());
        assertEquals(JobEvent.TimetableAction.EXPORT_NETEX_BLOCKS.name(), reported.getAction());
        assertEquals(JobEvent.State.PENDING, reported.getState());
    }

    @Test
    void aProviderWithBlocksExportDisabledSendsNothingToAshur() {
        consumer(false).handle(request());

        assertTrue(publisher.published().isEmpty());
        assertNull(blob(EXCHANGE, FOR_ASHUR));
    }
}

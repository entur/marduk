package no.rutebanken.marduk.otp;

import no.rutebanken.marduk.domain.OtpGraphsInfo;
import no.rutebanken.marduk.repository.InMemoryMardukBlobStoreRepository;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import no.rutebanken.marduk.services.OtpGraphsBlobStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtpGraphsTest {

    private final Map<String, Map<String, byte[]>> buckets = new ConcurrentHashMap<>();

    private InMemoryMardukBlobStoreRepository internalRepository;
    private InMemoryMardukBlobStoreRepository graphsRepository;
    private OtpGraphs otpGraphs;

    @BeforeEach
    void setUp() {
        internalRepository = new InMemoryMardukBlobStoreRepository(buckets);
        graphsRepository = new InMemoryMardukBlobStoreRepository(buckets);
        otpGraphs = new OtpGraphs(
                new OtpGraphsBlobStoreService("otp-graphs", graphsRepository),
                new MardukInternalBlobStoreService("marduk-internal", internalRepository),
                "graphs");
    }

    private static ByteArrayInputStream dummy() {
        return new ByteArrayInputStream("dummy".getBytes(StandardCharsets.UTF_8));
    }

    private static List<String> serializationIds(List<OtpGraphsInfo.OtpGraphFile> graphs) {
        return graphs.stream().map(OtpGraphsInfo.OtpGraphFile::serializationId).toList();
    }

    @Test
    void theTwoGraphKindsAreListedFromTheirOwnBuckets() {
        graphsRepository.uploadBlob("netex-otp2/EN-0051/20260819120000000-Graph-otp2-EN-0051.obj", dummy());
        internalRepository.uploadBlob("graphs/street/streetGraph-otp2-EN-0051.obj", dummy());

        OtpGraphsInfo graphs = otpGraphs.list();

        assertEquals(List.of("EN-0051"), serializationIds(graphs.transitGraphs()));
        assertEquals(List.of("EN-0051"), serializationIds(graphs.streetGraphs()));
    }

    @Test
    void onlyTheTwoMostRecentSerializationIdsAreListed() {
        graphsRepository.uploadBlob("netex-otp2/EN-0049/20260819120000000-Graph-otp2-EN-0049.obj", dummy());
        graphsRepository.uploadBlob("netex-otp2/EN-0050/20260819120000000-Graph-otp2-EN-0050.obj", dummy());
        graphsRepository.uploadBlob("netex-otp2/EN-0051/20260819120000000-Graph-otp2-EN-0051.obj", dummy());

        assertEquals(List.of("EN-0051", "EN-0050"), serializationIds(otpGraphs.list().transitGraphs()));
    }

    @Test
    void thePointerFilesBesideTheGraphsAreNotGraphs() {
        graphsRepository.uploadBlob("netex-otp2/EN-0051/20260819120000000-Graph-otp2-EN-0051.obj", dummy());
        graphsRepository.uploadBlob("netex-otp2/EN-0051/current-otp2", dummy());
        graphsRepository.uploadBlob("current-otp2", dummy());

        assertEquals(1, otpGraphs.list().transitGraphs().size());
    }

    @Test
    void anEmptyBucketListsNoGraphs() {
        OtpGraphsInfo graphs = otpGraphs.list();

        assertTrue(graphs.transitGraphs().isEmpty());
        assertTrue(graphs.streetGraphs().isEmpty());
    }
}

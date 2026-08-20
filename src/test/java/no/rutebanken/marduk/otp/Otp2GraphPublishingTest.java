package no.rutebanken.marduk.otp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Otp2GraphPublishingTest {

    private static final String WORK_DIR = "graphs/work/a-uuid/20260819120000000";

    @Test
    void theCompatibilityVersionIsTheGraphFileNamesSuffix() {
        assertEquals("EN-0051", Otp2GraphPublishing.graphCompatibilityVersion("Graph-otp2-EN-0051.obj"));
    }

    @Test
    void aGraphFileNameWithoutAVersionIsPublishedAsUnknownVersion() {
        assertEquals("unknown-version", Otp2GraphPublishing.graphCompatibilityVersion("Graph-otp2.obj"));
    }

    @Test
    void theStreetGraphKeepsItsFileNameSoARebuildOfTheSameVersionOverwrites() {
        Otp2GraphPublishing.BaseGraph paths =
                Otp2GraphPublishing.baseGraph(WORK_DIR, "streetGraph-otp2-EN-0051.obj", "graphs");

        assertEquals(WORK_DIR + "/streetGraph-otp2-EN-0051.obj", paths.builtPath());
        assertEquals("graphs/street/streetGraph-otp2-EN-0051.obj", paths.publishedPath());
    }

    @Test
    void theTransitGraphIsPublishedUnderItsVersionAndTimestampSoEveryBuildIsKept() {
        Otp2GraphPublishing.NetexGraph paths =
                Otp2GraphPublishing.netexGraph(WORK_DIR, "20260819120000000", "Graph-otp2-EN-0051.obj");

        assertEquals("EN-0051", paths.compatibilityVersion());
        assertEquals(WORK_DIR + "/Graph-otp2-EN-0051.obj", paths.builtPath());
        assertEquals("netex-otp2/EN-0051/20260819120000000-Graph-otp2-EN-0051.obj", paths.publishedPath());
        assertEquals("netex-otp2/20260819120000000-report", paths.reportVersion());
    }
}

package no.rutebanken.marduk.pubsub;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MardukQueuesTest {

    /** Five distinct projects, as in production. */
    private static final MardukQueues DEPLOYED = new MardukQueues(
            "ent-marduk-prd", "ent-antu-prd", "ent-nabu-prd", "ent-ashur-prd", "ent-servicelnk-prd");

    /** What tests and the compose stack configure: every project id is the same value. */
    private static final MardukQueues SINGLE_PROJECT =
            new MardukQueues("test", "test", "test", "test", "test");

    @Test
    void aDestinationInMardukSOwnProjectStaysUnqualified() {
        assertEquals("ChouetteImportQueue", DEPLOYED.topic(MardukQueues.CHOUETTE_IMPORT_QUEUE));
        assertEquals("ChouetteImportQueue", DEPLOYED.subscription(MardukQueues.CHOUETTE_IMPORT_QUEUE));
    }

    @Test
    void antuAndNabuTopicsAreQualifiedWithTheirOwnProject() {
        // Camel spelled the project into the endpoint URI; publishing these unqualified would send them to
        // a topic of the same name in marduk's project, which nothing reads.
        assertEquals(
                "projects/ent-antu-prd/topics/AntuNetexValidationQueue",
                DEPLOYED.topic(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE));
        assertEquals(
                "projects/ent-nabu-prd/topics/JobEventQueue",
                DEPLOYED.topic(MardukQueues.JOB_EVENT_QUEUE));
    }

    @Test
    void theAshurAndServicelinkerStatusSubscriptionsAreCrossProject() {
        // The topics live in marduk's project, but the subscriptions marduk reads are created by ashur's
        // and servicelinker's own terraform, in their projects.
        assertEquals(
                "projects/ent-ashur-prd/subscriptions/FilterNetexFileStatusQueue",
                DEPLOYED.subscription(MardukQueues.FILTER_NETEX_FILE_STATUS_QUEUE));
        assertEquals(
                "projects/ent-servicelnk-prd/subscriptions/ServicelinkerStatusQueue",
                DEPLOYED.subscription(MardukQueues.SERVICELINKER_STATUS_QUEUE));
    }

    @Test
    void nothingIsQualifiedWhenEveryProjectIsTheSame() {
        // Keeps EnturGooglePubSubAdmin working in tests and locally: it passes the destination name to both
        // createTopic and createSubscription, so a qualified name would be wrong for one of them.
        assertEquals("AntuNetexValidationQueue", SINGLE_PROJECT.topic(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE));
        assertEquals("JobEventQueue", SINGLE_PROJECT.topic(MardukQueues.JOB_EVENT_QUEUE));
        assertEquals(
                "FilterNetexFileStatusQueue",
                SINGLE_PROJECT.subscription(MardukQueues.FILTER_NETEX_FILE_STATUS_QUEUE));
    }

    @Test
    void publishOnlyDestinationsExcludeEverythingWithAConsumer() {
        // Anything with a consumer is created by the consumer base class; listing it here too would be
        // harmless but misleading. Anything without one has to be here or a fresh emulator 404s on publish.
        assertTrue(DEPLOYED.publishOnlyDestinations().contains(MardukQueues.JOB_EVENT_QUEUE));
        assertTrue(DEPLOYED.publishOnlyDestinations().contains(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE));
        assertTrue(DEPLOYED.publishOnlyDestinations().contains(MardukQueues.GTFS_ROUTE_DISPATCHER_TOPIC));
        assertTrue(DEPLOYED.publishOnlyDestinations().contains(MardukQueues.MARDUK_DEAD_LETTER_QUEUE));
        assertEquals(8, DEPLOYED.publishOnlyDestinations().size());
    }
}

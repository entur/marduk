package no.rutebanken.marduk.health;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PubSubSubscriberHealthIndicatorTest {

    private final PubSubSubscriberHealthIndicator indicator =
            new PubSubSubscriberHealthIndicator(List.of());

    @Test
    void everySubscriberRunningIsUp() {
        Health health = indicator.evaluate(Map.of("MardukInboundQueue", "RUNNING"));

        assertEquals(Status.UP, health.getStatus());
        assertEquals(1, health.getDetails().get("subscribers"));
    }

    @Test
    void aFailedSubscriberIsDownAndNamed() {
        // The failure this exists for: the pod keeps serving while a subscription is consumed by nobody.
        Health health = indicator.evaluate(
                Map.of("MardukInboundQueue", "RUNNING", "GtfsExportMergedQueue", "FAILED"));

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals(Map.of("GtfsExportMergedQueue", "FAILED"), health.getDetails().get("notRunning"));
    }

    @Test
    void aTerminatedSubscriberIsDownToo() {
        Health health = indicator.evaluate(Map.of("MardukInboundQueue", "TERMINATED"));

        assertEquals(Status.DOWN, health.getStatus());
    }

    @Test
    void noSubscriberAtAllIsDown() {
        assertEquals(Status.DOWN, indicator.evaluate(Map.of()).getStatus());
    }

    @Test
    void subscribersThatCannotBeReadAreUnknownRatherThanDown() {
        // A library upgrade that moves the field must not page anyone. MardukPubSubConsumerTest is what
        // catches the move itself.
        assertEquals(Status.UNKNOWN, indicator.evaluate(null).getStatus());
    }
}

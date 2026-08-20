package no.rutebanken.marduk.pubsub;

import no.rutebanken.marduk.pipeline.MardukMessage;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PubSubAttributesTest {

    @Test
    void everyOrdinaryHeaderIsPublished() {
        MardukMessage message = new MardukMessage()
                .setHeader(PROVIDER_ID, 2L)
                .setHeader(CORRELATION_ID, "corr")
                .setHeader(CHOUETTE_JOB_STATUS_ROUTING_DESTINATION, "direct:processImportResult");

        assertEquals(
                Map.of(
                        "RutebankenProviderId", "2",
                        "RutebankenCorrelationId", "corr",
                        "RutebankenChouetteJobStatusRoutingDestination", "direct:processImportResult"),
                PubSubAttributes.toAttributes(message));
    }

    @Test
    void authorizationIsNeverPublished() {
        // The sole guard keeping a bearer token copied off an inbound HTTP request out of a topic other
        // services read. Checked in three spellings because HTTP/2 lowercases header names.
        assertFalse(PubSubAttributes.toAttributes(
                new MardukMessage().setHeader("Authorization", "Bearer secret")).containsKey("Authorization"));
        assertTrue(PubSubAttributes.toAttributes(
                new MardukMessage().setHeader("authorization", "Bearer secret")).isEmpty());
        assertTrue(PubSubAttributes.toAttributes(
                new MardukMessage().setHeader("AUTHORIZATION", "Bearer secret")).isEmpty());
    }

    @Test
    void reservedGoogPrefixIsNeverPublished() {
        // PubSub rejects the whole publish with INVALID_ARGUMENT if one of these gets through, and the
        // streaming pull client hands us googclient_deliveryattempt as soon as a subscription gains a
        // dead-letter policy. Every Chouette poll re-publishes the inbound header map.
        MardukMessage message = new MardukMessage()
                .setHeader("googclient_deliveryattempt", "3")
                .setHeader("goog_anything", "x")
                .setHeader(PROVIDER_ID, 2L);

        assertEquals(Map.of("RutebankenProviderId", "2"), PubSubAttributes.toAttributes(message));
    }

    @Test
    void breadcrumbAndFrameworkHeadersAreNotPublished() {
        MardukMessage message = new MardukMessage()
                .setHeader("breadcrumbId", "ID-abc")
                .setHeader("CamelHttpMethod", "GET")
                .setHeader("CamelGooglePubsubMessageId", "42")
                .setHeader(PROVIDER_ID, 2L);

        assertEquals(Map.of("RutebankenProviderId", "2"), PubSubAttributes.toAttributes(message));
    }

    @Test
    void oversizedValuesAreDroppedRatherThanFailingThePublish() {
        MardukMessage message = new MardukMessage()
                .setHeader("short", "x".repeat(1024))
                .setHeader("tooLong", "x".repeat(1025));

        Map<String, String> attributes = PubSubAttributes.toAttributes(message);

        assertTrue(attributes.containsKey("short"), "1024 is the inclusive limit Camel applied");
        assertFalse(attributes.containsKey("tooLong"));
    }

    @Test
    void nullHeaderValuesBecomeEmptyStrings() {
        MardukMessage message = new MardukMessage().setHeader("present", null);

        assertEquals(Map.of("present", ""), PubSubAttributes.toAttributes(message));
    }

    @Test
    void inboundAttributesBecomeHeaders() {
        MardukMessage message = PubSubAttributes.toMessage(
                "payload".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Map.of("RutebankenProviderId", "2", "RutebankenCorrelationId", "corr"));

        assertEquals(2L, message.getHeader(PROVIDER_ID, Long.class));
        assertEquals("corr", message.getHeader(CORRELATION_ID, String.class));
        assertEquals("payload", message.getBody(String.class));
    }

    @Test
    void anInboundRoutingDestinationSurvivesARepublish() {
        // A Chouette poll is re-published every 30s for up to 3000 attempts, so a message written by the
        // previous version is still circulating long after a deploy. Its attributes have to round-trip.
        Map<String, String> inbound = Map.of(
                "RutebankenChouetteJobStatusRoutingDestination", "direct:processValidationResult",
                "RutebankenProviderId", "2",
                "loopCounter", "17");

        Map<String, String> republished =
                PubSubAttributes.toAttributes(PubSubAttributes.toMessage(new byte[0], inbound));

        assertEquals(inbound, republished);
    }
}

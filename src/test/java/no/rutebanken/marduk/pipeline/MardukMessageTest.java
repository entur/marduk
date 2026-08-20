package no.rutebanken.marduk.pipeline;

import no.rutebanken.marduk.routes.status.JobEvent;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MardukMessageTest {

    @Test
    void headerLookupIsCaseInsensitive() {
        MardukMessage message = new MardukMessage().setHeader("authorization", "Bearer secret");

        assertEquals("Bearer secret", message.getHeader("Authorization", String.class));
        assertEquals("Bearer secret", message.getHeader("AUTHORIZATION", String.class));
    }

    @Test
    void removingAHeaderIsCaseInsensitive() {
        // removeHttpHeaders() strips the literal "Authorization" while an HTTP/2 client sends it
        // lowercase. On a case-sensitive map the bearer token would survive into the call to Chouette.
        MardukMessage message = new MardukMessage().setHeader("authorization", "Bearer secret");

        message.removeHeader("Authorization");

        assertNull(message.getHeader("authorization"), "the token outlived removeHeader");
        assertFalse(message.hasHeader("Authorization"));
        assertTrue(message.getHeaders().isEmpty(), "a stale entry would still be published as an attribute");
    }

    @Test
    void theFirstSpellingOfAHeaderWins() {
        // Attribute names are case-sensitive on the wire, so the spelling has to be stable, and a header
        // must never appear twice under two cases. Three spellings rather than two: with two, an
        // implementation that tracks the *latest* spelling instead of the first still collapses them,
        // and only splits the entry in half on the third write.
        MardukMessage message = new MardukMessage()
                .setHeader("EnturDatasetReferential", "rb_tst")
                .setHeader("enturdatasetreferential", "rb_rut")
                .setHeader("ENTURDATASETREFERENTIAL", "rb_ovr");

        assertEquals(Map.of("EnturDatasetReferential", "rb_ovr"), message.getHeaders());
    }

    @Test
    void headersKeepInsertionOrder() {
        MardukMessage message = new MardukMessage()
                .setHeader("first", 1)
                .setHeader("second", 2)
                .setHeader("third", 3);

        assertEquals(List.of("first", "second", "third"), List.copyOf(message.getHeaders().keySet()));
    }

    @Test
    void removeHeadersStartingWithClearsAPrefix() {
        MardukMessage message = new MardukMessage()
                .setHeader("CamelHttpMethod", "GET")
                .setHeader("camelhttpuri", "http://chouette")
                .setHeader("RutebankenProviderId", 2L);

        message.removeHeadersStartingWith("CamelHttp");

        assertEquals(Map.of("RutebankenProviderId", 2L), message.getHeaders());
    }

    @Test
    void stringHeadersCoerceToTheTypeTheCallerAsks() {
        // Everything arriving as a PubSub attribute is a String; the same headers set in-process are
        // typed. Both paths work today only because Camel converted on read.
        MardukMessage fromAttributes = new MardukMessage()
                .setHeader("RutebankenProviderId", "2")
                .setHeader("loopCounter", "17")
                .setHeader("RutebankenApplyDuplicateFilter", "true")
                .setHeader("RutebankenChouetteJobStatusValidationLevel", "VALIDATION_LEVEL_2");

        assertEquals(2L, fromAttributes.getHeader("RutebankenProviderId", Long.class));
        assertEquals(17, fromAttributes.getHeader("loopCounter", Integer.class));
        assertEquals(true, fromAttributes.getHeader("RutebankenApplyDuplicateFilter", Boolean.class));
        assertEquals(
                JobEvent.TimetableAction.VALIDATION_LEVEL_2,
                fromAttributes.getHeader("RutebankenChouetteJobStatusValidationLevel", JobEvent.TimetableAction.class));
    }

    @Test
    void typedHeadersCoerceAcrossNumericTypes() {
        MardukMessage inProcess = new MardukMessage().setHeader("RutebankenProviderId", 2L);

        assertEquals("2", inProcess.getHeader("RutebankenProviderId", String.class));
        assertEquals(2, inProcess.getHeader("RutebankenProviderId", Integer.class));
    }

    @Test
    void missingHeadersFallBackToTheDefault() {
        MardukMessage message = new MardukMessage();

        assertEquals(0, message.getHeader("loopCounter", 0, Integer.class));
        assertNull(message.getHeader("loopCounter", Integer.class));
        assertEquals(1, message.setHeader("loopCounter", 1).getHeader("loopCounter", 0, Integer.class));
    }

    @Test
    void convertingAStreamBodyLeavesItReadable() throws IOException {
        // Camel's per-route stream caching hid the fact that reading an InputStream consumes it; the
        // upload path already carries a workaround for a case where the stream was gone by upload time.
        MardukMessage message = new MardukMessage()
                .setBody(new ByteArrayInputStream("dataset".getBytes(StandardCharsets.UTF_8)));

        assertEquals("dataset", message.getBody(String.class));
        assertEquals("dataset", message.getBody(String.class), "the body was consumed by the first read");
        assertArrayEquals("dataset".getBytes(StandardCharsets.UTF_8), message.getBody(byte[].class));
        assertEquals("dataset", new String(message.getBody(InputStream.class).readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void bodyConvertsBetweenTextAndBytes() {
        assertArrayEquals(
                "netex".getBytes(StandardCharsets.UTF_8),
                new MardukMessage().setBody("netex").getBody(byte[].class));
        assertEquals(
                "netex",
                new MardukMessage().setBody("netex".getBytes(StandardCharsets.UTF_8)).getBody(String.class));
    }

    @Test
    void bodyOfAnUnconvertibleTypePassesThroughUnchanged() {
        List<String> files = List.of("a.zip", "b.zip");
        MardukMessage message = new MardukMessage().setBody(files);

        assertSame(files, message.getBody(List.class));
        assertThrows(IllegalArgumentException.class, () -> message.getBody(Long.class));
    }

    @Test
    void nullBodyAndHeadersConvertToNullRatherThanThrowing() {
        MardukMessage message = new MardukMessage();

        assertNull(message.getBody(String.class));
        assertNull(message.getHeader("absent", Long.class));
    }

    @Test
    void propertiesAreSeparateFromHeaders() {
        // Exchange properties never reached the wire; headers did. Keeping them apart is what stops an
        // internal value such as chouette_url becoming a published attribute.
        MardukMessage message = new MardukMessage()
                .setProperty("chouette_url", "http://chouette/jobs")
                .setHeader("RutebankenProviderId", 2L);

        assertEquals("http://chouette/jobs", message.getProperty("chouette_url", String.class));
        assertNull(message.getHeader("chouette_url"));
        assertEquals(Map.of("RutebankenProviderId", 2L), message.getHeaders());
    }

    @Test
    void copyIsIndependent() {
        MardukMessage original = new MardukMessage()
                .setHeader("RutebankenCorrelationId", "corr")
                .setProperty("prop", "value")
                .setBody("body");

        MardukMessage copy = original.copy();
        copy.setHeader("RutebankenCorrelationId", "other").setProperty("prop", "other").setBody("other");

        assertEquals("corr", original.getHeader("RutebankenCorrelationId", String.class));
        assertEquals("value", original.getProperty("prop", String.class));
        assertEquals("body", original.getBody());
    }

    @Test
    void copyPreservesHeaderCaseInsensitivity() {
        MardukMessage copy = new MardukMessage().setHeader("authorization", "Bearer secret").copy();

        copy.removeHeader("Authorization");

        assertTrue(copy.getHeaders().isEmpty(), "the copy lost the case-insensitive index");
    }

    @Test
    void setHeadersReplacesTheWholeMap() {
        MardukMessage message = new MardukMessage().setHeader("old", "value");

        message.setHeaders(Map.of("RutebankenProviderId", 2L));

        assertEquals(Map.of("RutebankenProviderId", 2L), message.getHeaders());
    }
}

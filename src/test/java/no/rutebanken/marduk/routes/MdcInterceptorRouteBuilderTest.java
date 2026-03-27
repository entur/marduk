/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.routes;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MdcInterceptorRouteBuilderTest extends MardukRouteBuilderIntegrationTestBase {

    @Produce("direct:testMdc")
    protected ProducerTemplate testMdcTemplate;

    @Produce("direct:testMdcCleanup")
    protected ProducerTemplate testMdcCleanupTemplate;

    @EndpointInject("mock:mdcResult")
    protected MockEndpoint mdcResultMock;

    @EndpointInject("mock:mdcCleanupResult")
    protected MockEndpoint mdcCleanupResultMock;

    @Test
    void testMdcSetFromCorrelationIdAndDatasetReferential() throws Exception {
        AdviceWith.adviceWith(context, "test-mdc-capture", a -> {});
        context.start();

        mdcResultMock.expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<>();
        headers.put(Constants.CORRELATION_ID, "test-correlation-123");
        headers.put(Constants.DATASET_REFERENTIAL, "TST");
        testMdcTemplate.sendBodyAndHeaders("", headers);

        mdcResultMock.assertIsSatisfied();
        assertEquals("test-correlation-123", mdcResultMock.getExchanges().get(0).getIn().getHeader("capturedCorrelationId"));
        assertEquals("TST", mdcResultMock.getExchanges().get(0).getIn().getHeader("capturedCodespace"));
    }

    @Test
    void testMdcCodespaceFallsThroughToChouetteReferential() throws Exception {
        AdviceWith.adviceWith(context, "test-mdc-capture", a -> {});
        context.start();

        mdcResultMock.expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<>();
        headers.put(Constants.CORRELATION_ID, "test-correlation-456");
        headers.put(Constants.CHOUETTE_REFERENTIAL, "rb_TST");
        testMdcTemplate.sendBodyAndHeaders("", headers);

        mdcResultMock.assertIsSatisfied();
        assertEquals("test-correlation-456", mdcResultMock.getExchanges().get(0).getIn().getHeader("capturedCorrelationId"));
        assertEquals("rb_TST", mdcResultMock.getExchanges().get(0).getIn().getHeader("capturedCodespace"));
    }

    @Test
    void testMdcDatasetReferentialTakesPrecedenceOverChouetteReferential() throws Exception {
        AdviceWith.adviceWith(context, "test-mdc-capture", a -> {});
        context.start();

        mdcResultMock.expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<>();
        headers.put(Constants.DATASET_REFERENTIAL, "TST");
        headers.put(Constants.CHOUETTE_REFERENTIAL, "rb_TST");
        testMdcTemplate.sendBodyAndHeaders("", headers);

        mdcResultMock.assertIsSatisfied();
        assertEquals("TST", mdcResultMock.getExchanges().get(0).getIn().getHeader("capturedCodespace"));
    }

    @Test
    void testMdcIgnoresEmptyHeaders() throws Exception {
        AdviceWith.adviceWith(context, "test-mdc-capture", a -> {});
        context.start();

        mdcResultMock.expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<>();
        headers.put(Constants.CORRELATION_ID, "");
        headers.put(Constants.DATASET_REFERENTIAL, "");
        headers.put(Constants.CHOUETTE_REFERENTIAL, "");
        testMdcTemplate.sendBodyAndHeaders("", headers);

        mdcResultMock.assertIsSatisfied();
        assertNull(mdcResultMock.getExchanges().get(0).getIn().getHeader("capturedCorrelationId"));
        assertNull(mdcResultMock.getExchanges().get(0).getIn().getHeader("capturedCodespace"));
    }

    @Test
    void testMdcClearedWhenHeadersAbsent() throws Exception {
        AdviceWith.adviceWith(context, "test-mdc-capture", a -> {});
        context.start();

        mdcResultMock.expectedMessageCount(1);

        // Send with no MDC-related headers at all
        testMdcTemplate.sendBody("");

        mdcResultMock.assertIsSatisfied();
        assertNull(mdcResultMock.getExchanges().get(0).getIn().getHeader("capturedCorrelationId"));
        assertNull(mdcResultMock.getExchanges().get(0).getIn().getHeader("capturedCodespace"));
    }

    @Test
    void testMdcEmptyDatasetReferentialFallsThroughToChouetteReferential() throws Exception {
        AdviceWith.adviceWith(context, "test-mdc-capture", a -> {});
        context.start();

        mdcResultMock.expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<>();
        headers.put(Constants.DATASET_REFERENTIAL, "");
        headers.put(Constants.CHOUETTE_REFERENTIAL, "rb_TST");
        testMdcTemplate.sendBodyAndHeaders("", headers);

        mdcResultMock.assertIsSatisfied();
        assertEquals("rb_TST", mdcResultMock.getExchanges().get(0).getIn().getHeader("capturedCodespace"));
    }

    @Test
    void testMdcClearedOnExchangeCompletion() throws Exception {
        AdviceWith.adviceWith(context, "test-mdc-cleanup", a -> {});
        context.start();

        mdcCleanupResultMock.expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<>();
        headers.put(Constants.CORRELATION_ID, "will-be-cleaned");
        headers.put(Constants.DATASET_REFERENTIAL, "CLEANUP_TST");
        testMdcCleanupTemplate.sendBodyAndHeaders("", headers);

        mdcCleanupResultMock.assertIsSatisfied();

        // After the exchange completes, onCompletion should have cleared MDC
        assertNull(MDC.get("correlationId"));
        assertNull(MDC.get("codespace"));
    }

    @Test
    void testStaleMdcClearedBySubsequentExchangeWithoutHeaders() throws Exception {
        AdviceWith.adviceWith(context, "test-mdc-capture", a -> {});
        context.start();

        // First exchange sets MDC values
        mdcResultMock.expectedMessageCount(2);

        Map<String, Object> headers = new HashMap<>();
        headers.put(Constants.CORRELATION_ID, "first-exchange");
        headers.put(Constants.DATASET_REFERENTIAL, "FIRST");
        testMdcTemplate.sendBodyAndHeaders("", headers);

        // Second exchange has no MDC headers -- should NOT inherit stale values
        testMdcTemplate.sendBody("");

        mdcResultMock.assertIsSatisfied();
        assertEquals("first-exchange", mdcResultMock.getExchanges().get(0).getIn().getHeader("capturedCorrelationId"));
        assertEquals("FIRST", mdcResultMock.getExchanges().get(0).getIn().getHeader("capturedCodespace"));
        assertNull(mdcResultMock.getExchanges().get(1).getIn().getHeader("capturedCorrelationId"));
        assertNull(mdcResultMock.getExchanges().get(1).getIn().getHeader("capturedCodespace"));
    }

    @Test
    void testSetMdcCodespaceIfMissingSetsWhenAbsent() {
        MDC.remove("codespace");
        BaseRouteBuilder.setMdcCodespaceIfMissing("NEW_CS");
        assertEquals("NEW_CS", MDC.get("codespace"));
        MDC.remove("codespace");
    }

    @Test
    void testSetMdcCodespaceIfMissingDoesNotOverwrite() {
        MDC.put("codespace", "EXISTING");
        BaseRouteBuilder.setMdcCodespaceIfMissing("NEW_CS");
        assertEquals("EXISTING", MDC.get("codespace"));
        MDC.remove("codespace");
    }

    @Test
    void testSetMdcCodespaceIfMissingOverwritesEmpty() {
        MDC.put("codespace", "");
        BaseRouteBuilder.setMdcCodespaceIfMissing("NEW_CS");
        assertEquals("NEW_CS", MDC.get("codespace"));
        MDC.remove("codespace");
    }

    @Test
    void testSetMdcCodespaceIfMissingIgnoresNull() {
        MDC.remove("codespace");
        BaseRouteBuilder.setMdcCodespaceIfMissing(null);
        assertNull(MDC.get("codespace"));
    }

    @Test
    void testSetMdcCodespaceIfMissingIgnoresEmpty() {
        MDC.remove("codespace");
        BaseRouteBuilder.setMdcCodespaceIfMissing("");
        assertNull(MDC.get("codespace"));
    }
}

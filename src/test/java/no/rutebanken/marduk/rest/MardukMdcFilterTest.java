package no.rutebanken.marduk.rest;

import no.rutebanken.marduk.Constants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MardukMdcFilterTest {

    private static final String CORRELATION_ID = "correlationId";
    private static final String CODESPACE = "codespace";

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void theRequestsCorrelationIdIsSetForTheHandlerAndClearedAfterwards() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(Constants.CORRELATION_ID, "corr");
        request.addHeader(Constants.CHOUETTE_REFERENTIAL, "rb_tst");

        Map<String, String> whileHandling = capture(request);

        assertEquals("corr", whileHandling.get(CORRELATION_ID));
        assertEquals("rb_tst", whileHandling.get(CODESPACE));
        assertNull(MDC.get(CORRELATION_ID));
        assertNull(MDC.get(CODESPACE));
    }

    @Test
    void aRequestDoesNotSeeThePreviousRequestsContext() throws Exception {
        MDC.put(CORRELATION_ID, "the-previous-request");
        MDC.put(CODESPACE, "rb_previous");

        Map<String, String> whileHandling = capture(new MockHttpServletRequest());

        assertNull(whileHandling.get(CORRELATION_ID));
        assertNull(whileHandling.get(CODESPACE));
    }

    private static Map<String, String> capture(MockHttpServletRequest request) throws Exception {
        Map<String, String> seen = new HashMap<>();
        new MardukMdcFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            seen.put(CORRELATION_ID, MDC.get(CORRELATION_ID));
            seen.put(CODESPACE, MDC.get(CODESPACE));
        });
        return seen;
    }
}

package no.rutebanken.marduk.routes.experimental;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static no.rutebanken.marduk.routes.experimental.AshurFilteringReportValidator.FILTERING_REPORT_ERROR_HEADER;
import static no.rutebanken.marduk.routes.experimental.AshurFilteringReportValidator.FILTERING_REPORT_VALID_HEADER;
import static org.junit.jupiter.api.Assertions.*;

class AshurFilteringReportValidatorTest {

    private final AshurFilteringReportValidator validator = new AshurFilteringReportValidator();

    @Test
    void validStandardImportReport() throws Exception {
        Exchange exchange = exchangeWithBody(reportJson("StandardImportFilter", """
            "ServiceJourney": 142, "Line": 3, "Block": 0"""));

        validator.validateStandardImportReport(exchange);

        assertTrue(exchange.getIn().getHeader(FILTERING_REPORT_VALID_HEADER, Boolean.class));
    }

    @Test
    void standardImportReportWithNoBlockKey() throws Exception {
        Exchange exchange = exchangeWithBody(reportJson("StandardImportFilter", """
            "ServiceJourney": 142, "Line": 3"""));

        validator.validateStandardImportReport(exchange);

        assertTrue(exchange.getIn().getHeader(FILTERING_REPORT_VALID_HEADER, Boolean.class));
    }

    @Test
    void standardImportReportWithNullEntityTypeCounts() throws Exception {
        Exchange exchange = exchangeWithBody("""
            {
              "created": "2026-03-27T12:00:00.000000",
              "correlationId": "abc-123",
              "codespace": "TST",
              "filterProfile": "StandardImportFilter",
              "status": "FAILED",
              "reason": "Something went wrong",
              "entityTypeCounts": null
            }
            """);

        validator.validateStandardImportReport(exchange);

        assertTrue(exchange.getIn().getHeader(FILTERING_REPORT_VALID_HEADER, Boolean.class));
    }

    @Test
    void standardImportFailsWithWrongProfile() throws Exception {
        Exchange exchange = exchangeWithBody(reportJson("IncludeBlocksAndRestrictedJourneysFilter", """
            "ServiceJourney": 142"""));

        validator.validateStandardImportReport(exchange);

        assertFalse(exchange.getIn().getHeader(FILTERING_REPORT_VALID_HEADER, Boolean.class));
        String error = exchange.getIn().getHeader(FILTERING_REPORT_ERROR_HEADER, String.class);
        assertTrue(error.contains("StandardImportFilter"));
        assertTrue(error.contains("IncludeBlocksAndRestrictedJourneysFilter"));
    }

    @Test
    void standardImportFailsWithBlocks() throws Exception {
        Exchange exchange = exchangeWithBody(reportJson("StandardImportFilter", """
            "ServiceJourney": 142, "Block": 50"""));

        validator.validateStandardImportReport(exchange);

        assertFalse(exchange.getIn().getHeader(FILTERING_REPORT_VALID_HEADER, Boolean.class));
        String error = exchange.getIn().getHeader(FILTERING_REPORT_ERROR_HEADER, String.class);
        assertTrue(error.contains("Block"));
        assertTrue(error.contains("50"));
    }

    @Test
    void validBlocksExportReport() throws Exception {
        Exchange exchange = exchangeWithBody(reportJson("IncludeBlocksAndRestrictedJourneysFilter", """
            "ServiceJourney": 142, "Block": 50"""));

        validator.validateBlocksExportReport(exchange);

        assertTrue(exchange.getIn().getHeader(FILTERING_REPORT_VALID_HEADER, Boolean.class));
    }

    @Test
    void blocksExportFailsWithWrongProfile() throws Exception {
        Exchange exchange = exchangeWithBody(reportJson("StandardImportFilter", """
            "ServiceJourney": 142, "Block": 50"""));

        validator.validateBlocksExportReport(exchange);

        assertFalse(exchange.getIn().getHeader(FILTERING_REPORT_VALID_HEADER, Boolean.class));
        String error = exchange.getIn().getHeader(FILTERING_REPORT_ERROR_HEADER, String.class);
        assertTrue(error.contains("IncludeBlocksAndRestrictedJourneysFilter"));
        assertTrue(error.contains("StandardImportFilter"));
    }

    private static String reportJson(String filterProfile, String entityTypeCounts) {
        return """
            {
              "created": "2026-03-27T12:00:00.000000",
              "correlationId": "abc-123",
              "codespace": "TST",
              "filterProfile": "%s",
              "status": "SUCCESS",
              "reason": null,
              "entityTypeCounts": {%s}
            }
            """.formatted(filterProfile, entityTypeCounts);
    }

    private static Exchange exchangeWithBody(String body) {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setBody(body);
        return exchange;
    }
}

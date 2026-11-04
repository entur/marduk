package no.rutebanken.marduk.routes.experimental;

import no.rutebanken.marduk.exceptions.MardukException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AshurFilteringReportValidatorTest {

    private static final String CORRELATION_ID = "abc-123";

    private final AshurFilteringReportValidator validator = new AshurFilteringReportValidator();

    @Test
    void validStandardImportReport() {
        AshurFilteringReportValidator.Verdict verdict = validator.validateStandardImportReport(
                reportJson("StandardImportFilter", """
                        "ServiceJourney": 142, "Line": 3, "Block": 0"""), CORRELATION_ID);

        assertTrue(verdict.valid());
        assertNull(verdict.reason());
    }

    @Test
    void standardImportReportWithNoBlockKey() {
        assertTrue(validator.validateStandardImportReport(
                reportJson("StandardImportFilter", """
                        "ServiceJourney": 142, "Line": 3"""), CORRELATION_ID).valid());
    }

    @Test
    void standardImportReportWithNullEntityTypeCounts() {
        assertTrue(validator.validateStandardImportReport("""
                {
                  "created": "2026-03-27T12:00:00.000000",
                  "correlationId": "abc-123",
                  "codespace": "TST",
                  "filterProfile": "StandardImportFilter",
                  "status": "FAILED",
                  "reason": "Something went wrong",
                  "entityTypeCounts": null
                }
                """, CORRELATION_ID).valid());
    }

    @Test
    void standardImportFailsWithWrongProfile() {
        AshurFilteringReportValidator.Verdict verdict = validator.validateStandardImportReport(
                reportJson("IncludeBlocksAndRestrictedJourneysFilter", """
                        "ServiceJourney": 142"""), CORRELATION_ID);

        assertFalse(verdict.valid());
        assertTrue(verdict.reason().contains("StandardImportFilter"));
        assertTrue(verdict.reason().contains("IncludeBlocksAndRestrictedJourneysFilter"));
    }

    @Test
    void standardImportFailsWithBlocks() {
        // The security control: the standard import profile must not return blocks.
        AshurFilteringReportValidator.Verdict verdict = validator.validateStandardImportReport(
                reportJson("StandardImportFilter", """
                        "ServiceJourney": 142, "Block": 50"""), CORRELATION_ID);

        assertFalse(verdict.valid());
        assertTrue(verdict.reason().contains("Block"));
        assertTrue(verdict.reason().contains("50"));
    }

    @Test
    void validBlocksExportReport() {
        assertTrue(validator.validateBlocksExportReport(
                reportJson("IncludeBlocksAndRestrictedJourneysFilter", """
                        "ServiceJourney": 142, "Block": 50"""), CORRELATION_ID).valid());
    }

    @Test
    void blocksExportFailsWithWrongProfile() {
        AshurFilteringReportValidator.Verdict verdict = validator.validateBlocksExportReport(
                reportJson("StandardImportFilter", """
                        "ServiceJourney": 142, "Block": 50"""), CORRELATION_ID);

        assertFalse(verdict.valid());
        assertTrue(verdict.reason().contains("IncludeBlocksAndRestrictedJourneysFilter"));
        assertTrue(verdict.reason().contains("StandardImportFilter"));
    }

    @Test
    void anUnreadableReportFailsRatherThanPassing() {
        // A malformed report must not be treated as valid: it is the only thing standing between a
        // wrongly-filtered dataset and post-validation.
        MardukException thrown = assertThrows(MardukException.class,
                () -> validator.validateStandardImportReport("not json", CORRELATION_ID));

        assertEquals("Could not read the Ashur filtering report", thrown.getMessage());
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
}

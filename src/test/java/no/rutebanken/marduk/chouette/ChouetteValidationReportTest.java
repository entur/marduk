package no.rutebanken.marduk.chouette;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChouetteValidationReportTest {

    @Test
    void aReportWithNoFailedErrorCheckpointPasses() throws IOException {
        // The report still contains WARNING/NOK and ERROR/UNCHECK checkpoints, which are not failures.
        assertEquals("OK", ChouetteValidationReport.verdict(report("getValidationReportResponseOK.json")));
    }

    @Test
    void oneCheckpointThatIsBothErrorAndNokFailsTheReport() throws IOException {
        assertEquals("NOK", ChouetteValidationReport.verdict(report("getValidationReportResponseNOK.json")));
    }

    @Test
    void aWarningThatFailedIsNotAFailure() {
        assertEquals("OK", ChouetteValidationReport.verdict(
                "{\"validation_report\":{\"check_points\":[{\"severity\":\"WARNING\",\"result\":\"NOK\"}]}}"));
    }

    @Test
    void anErrorThatWasNeverCheckedIsNotAFailure() {
        assertEquals("OK", ChouetteValidationReport.verdict(
                "{\"validation_report\":{\"check_points\":[{\"severity\":\"ERROR\",\"result\":\"UNCHECK\"}]}}"));
    }

    @Test
    void aReportWithoutCheckpointsPasses() {
        assertEquals("OK", ChouetteValidationReport.verdict("{\"validation_report\":{\"result\":\"OK\"}}"));
    }

    private static String report(String name) throws IOException {
        try (InputStream json = ChouetteValidationReportTest.class
                .getResourceAsStream("/no/rutebanken/marduk/chouette/" + name)) {
            return new String(json.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

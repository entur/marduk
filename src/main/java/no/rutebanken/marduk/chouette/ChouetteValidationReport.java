package no.rutebanken.marduk.chouette;

import com.fasterxml.jackson.databind.JsonNode;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.json.ObjectMapperFactory;

import java.io.IOException;

/**
 * Whether a Chouette validation report contains a failure worth stopping for.
 *
 * <p>One checkpoint that is both {@code ERROR} and {@code NOK} is enough. A {@code WARNING} that is
 * {@code NOK}, or an {@code ERROR} that was never checked, is not: the reports routinely contain both.
 *
 * <p>Was the jsonpath predicate
 * {@code $.validation_report.check_points[?(@.severity == 'ERROR' && @.result == 'NOK')]}.
 */
final class ChouetteValidationReport {

    static final String OK = "OK";
    static final String NOK = "NOK";

    /** Set when Chouette produced no validation report at all, which is not a failure. */
    static final String NOT_PRESENT = "NOT_PRESENT";

    private ChouetteValidationReport() {
    }

    static String verdict(String json) {
        JsonNode checkPoints = parse(json).path("validation_report").path("check_points");
        for (JsonNode checkPoint : checkPoints) {
            if ("ERROR".equals(checkPoint.path("severity").asText())
                    && NOK.equals(checkPoint.path("result").asText())) {
                return NOK;
            }
        }
        return OK;
    }

    private static JsonNode parse(String json) {
        try {
            return ObjectMapperFactory.getSharedObjectMapper().readTree(json);
        } catch (IOException e) {
            throw new MardukException("Could not read the validation report Chouette returned", e);
        }
    }
}

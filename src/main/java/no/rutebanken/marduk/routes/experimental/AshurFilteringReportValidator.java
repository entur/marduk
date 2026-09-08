package no.rutebanken.marduk.routes.experimental;

import com.fasterxml.jackson.databind.ObjectReader;
import no.rutebanken.marduk.domain.AshurFilteringReport;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.json.ObjectMapperFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static no.rutebanken.marduk.Constants.FILTERING_PROFILE_INCLUDE_BLOCKS_AND_RESTRICTED_JOURNEYS;
import static no.rutebanken.marduk.Constants.FILTERING_PROFILE_STANDARD_IMPORT;

/**
 * Checks that Ashur filtered with the profile marduk asked for.
 *
 * <p>This is a security control, not a sanity check: the standard import profile must not return blocks, and
 * a report claiming a different profile than the one requested means the wrong filter ran.
 *
 * <p>Returns a {@link Verdict} rather than writing headers onto the message. The header protocol it used to
 * use, {@code FilteringReportValid} plus {@code FilteringReportError}, was only ever read by the route that
 * called it, one line later.
 */
@Component
public class AshurFilteringReportValidator {

    private static final Logger LOG = LoggerFactory.getLogger(AshurFilteringReportValidator.class);

    private static final ObjectReader REPORT_READER =
        ObjectMapperFactory.getSharedObjectMapper().readerFor(AshurFilteringReport.class);

    /** Valid, or invalid with the reason to report as the error code's detail. */
    public record Verdict(boolean valid, String reason) {

        static final Verdict PASSED = new Verdict(true, null);
    }

    public Verdict validateStandardImportReport(String reportBody, String correlationId) {
        AshurFilteringReport report = deserialize(reportBody);

        LOG.info("[correlationId={}] Validating Ashur filtering report for standard import. filterProfile='{}', status='{}', entityTypeCounts={}",
                correlationId, report.filterProfile(), report.status(), report.entityTypeCounts());

        if (!FILTERING_PROFILE_STANDARD_IMPORT.equals(report.filterProfile())) {
            return invalid(correlationId, "Expected filterProfile '" + FILTERING_PROFILE_STANDARD_IMPORT
                    + "' but got '" + report.filterProfile() + "'");
        }

        if (report.entityTypeCounts() != null) {
            long blockCount = report.entityTypeCounts().getOrDefault("Block", 0L);
            if (blockCount > 0) {
                return invalid(correlationId, "Standard import report contains " + blockCount + " Block entities");
            }
        }

        LOG.info("[correlationId={}] Filtering report validation passed for standard import.", correlationId);
        return Verdict.PASSED;
    }

    public Verdict validateBlocksExportReport(String reportBody, String correlationId) {
        AshurFilteringReport report = deserialize(reportBody);

        LOG.info("[correlationId={}] Validating Ashur filtering report for blocks export. filterProfile='{}', status='{}', entityTypeCounts={}",
                correlationId, report.filterProfile(), report.status(), report.entityTypeCounts());

        if (!FILTERING_PROFILE_INCLUDE_BLOCKS_AND_RESTRICTED_JOURNEYS.equals(report.filterProfile())) {
            return invalid(correlationId, "Expected filterProfile '"
                    + FILTERING_PROFILE_INCLUDE_BLOCKS_AND_RESTRICTED_JOURNEYS
                    + "' but got '" + report.filterProfile() + "'");
        }

        LOG.info("[correlationId={}] Filtering report validation passed for blocks export.", correlationId);
        return Verdict.PASSED;
    }

    private static Verdict invalid(String correlationId, String reason) {
        LOG.error("[correlationId={}] Filtering report validation failed: {}", correlationId, reason);
        return new Verdict(false, reason);
    }

    private AshurFilteringReport deserialize(String reportBody) {
        try {
            return REPORT_READER.readValue(reportBody);
        } catch (IOException e) {
            throw new MardukException("Could not read the Ashur filtering report", e);
        }
    }
}

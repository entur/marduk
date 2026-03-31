package no.rutebanken.marduk.routes.experimental;

import com.fasterxml.jackson.databind.ObjectReader;
import no.rutebanken.marduk.domain.AshurFilteringReport;
import no.rutebanken.marduk.json.ObjectMapperFactory;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.*;

@Component
public class AshurFilteringReportValidator {

    private static final Logger LOG = LoggerFactory.getLogger(AshurFilteringReportValidator.class);

    static final String FILTERING_REPORT_VALID_HEADER = "FilteringReportValid";
    static final String FILTERING_REPORT_ERROR_HEADER = "FilteringReportError";

    private static final ObjectReader REPORT_READER =
        ObjectMapperFactory.getSharedObjectMapper().readerFor(AshurFilteringReport.class);

    public void validateStandardImportReport(Exchange exchange) throws Exception {
        AshurFilteringReport report = deserialize(exchange);
        String correlationId = exchange.getIn().getHeader(CORRELATION_ID, String.class);

        LOG.info("[correlationId={}] Validating Ashur filtering report for standard import. filterProfile='{}', status='{}', entityTypeCounts={}",
                correlationId, report.filterProfile(), report.status(), report.entityTypeCounts());

        if (!FILTERING_PROFILE_STANDARD_IMPORT.equals(report.filterProfile())) {
            String reason = "Expected filterProfile '" + FILTERING_PROFILE_STANDARD_IMPORT + "' but got '" + report.filterProfile() + "'";
            LOG.error("[correlationId={}] Filtering report validation failed: {}", correlationId, reason);
            setInvalid(exchange, reason);
            return;
        }

        if (report.entityTypeCounts() != null) {
            long blockCount = report.entityTypeCounts().getOrDefault("Block", 0L);
            if (blockCount > 0) {
                String reason = "Standard import report contains " + blockCount + " Block entities";
                LOG.error("[correlationId={}] Filtering report validation failed: {}", correlationId, reason);
                setInvalid(exchange, reason);
                return;
            }
        }

        LOG.info("[correlationId={}] Filtering report validation passed for standard import.", correlationId);
        setValid(exchange);
    }

    public void validateBlocksExportReport(Exchange exchange) throws Exception {
        AshurFilteringReport report = deserialize(exchange);
        String correlationId = exchange.getIn().getHeader(CORRELATION_ID, String.class);

        LOG.info("[correlationId={}] Validating Ashur filtering report for blocks export. filterProfile='{}', status='{}', entityTypeCounts={}",
                correlationId, report.filterProfile(), report.status(), report.entityTypeCounts());

        if (!FILTERING_PROFILE_INCLUDE_BLOCKS_AND_RESTRICTED_JOURNEYS.equals(report.filterProfile())) {
            String reason = "Expected filterProfile '" + FILTERING_PROFILE_INCLUDE_BLOCKS_AND_RESTRICTED_JOURNEYS + "' but got '" + report.filterProfile() + "'";
            LOG.error("[correlationId={}] Filtering report validation failed: {}", correlationId, reason);
            setInvalid(exchange, reason);
            return;
        }

        LOG.info("[correlationId={}] Filtering report validation passed for blocks export.", correlationId);
        setValid(exchange);
    }

    private AshurFilteringReport deserialize(Exchange exchange) throws Exception {
        String body = exchange.getIn().getBody(String.class);
        return REPORT_READER.readValue(body);
    }

    private void setValid(Exchange exchange) {
        exchange.getIn().setHeader(FILTERING_REPORT_VALID_HEADER, true);
        exchange.getIn().removeHeader(FILTERING_REPORT_ERROR_HEADER);
    }

    private void setInvalid(Exchange exchange, String reason) {
        exchange.getIn().setHeader(FILTERING_REPORT_VALID_HEADER, false);
        exchange.getIn().setHeader(FILTERING_REPORT_ERROR_HEADER, reason);
    }
}

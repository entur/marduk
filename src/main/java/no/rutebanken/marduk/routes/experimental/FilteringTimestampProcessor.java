package no.rutebanken.marduk.routes.experimental;

import no.rutebanken.marduk.repository.FileNameAndDigestIdempotentRepository;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.FILTERING_FILE_CREATED_TIMESTAMP;

/**
 * Processor that resolves the createdAt timestamp for a file from the idempotent repository
 * and sets it as the FILTERING_FILE_CREATED_TIMESTAMP header.
 * This timestamp is used by downstream processors (e.g. NisabaHeadersProcessor, Ashur)
 * to ensure consistent naming across the pipeline.
 */
public class FilteringTimestampProcessor implements Processor {

    private static final Logger LOGGER = LoggerFactory.getLogger(FilteringTimestampProcessor.class);

    private final FileNameAndDigestIdempotentRepository fileNameAndDigestIdempotentRepository;

    public FilteringTimestampProcessor(FileNameAndDigestIdempotentRepository fileNameAndDigestIdempotentRepository) {
        this.fileNameAndDigestIdempotentRepository = fileNameAndDigestIdempotentRepository;
    }

    @Override
    public void process(Exchange exchange) {
        String fileName = exchange.getIn().getHeader(FILE_NAME, String.class);
        LOGGER.info("Looking up createdAt timestamp for file '{}'", fileName);

        LocalDateTime createdAt = fileNameAndDigestIdempotentRepository.getCreatedAt(fileName);
        if (createdAt != null) {
            LOGGER.info("Found createdAt timestamp {} in idempotent repository for file '{}'", createdAt, fileName);
        } else {
            createdAt = LocalDateTime.now();
            LOGGER.warn("No createdAt timestamp found in idempotent repository for file '{}', falling back to current time: {}", fileName, createdAt);
        }

        exchange.getIn().setHeader(FILTERING_FILE_CREATED_TIMESTAMP, createdAt.toString());
    }
}

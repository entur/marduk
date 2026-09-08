package no.rutebanken.marduk.routes.experimental;

import no.rutebanken.marduk.repository.FileNameAndDigestIdempotentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * Resolves when a file was first seen, from the idempotent repository.
 *
 * <p>The timestamp is what keeps the file's name consistent across the pipeline: Nisaba and Ashur both name
 * their output after it, so re-deriving it at each hop would produce two different names for one import.
 */
public class FilteringTimestampProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(FilteringTimestampProcessor.class);

    private final FileNameAndDigestIdempotentRepository fileNameAndDigestIdempotentRepository;

    public FilteringTimestampProcessor(FileNameAndDigestIdempotentRepository fileNameAndDigestIdempotentRepository) {
        this.fileNameAndDigestIdempotentRepository = fileNameAndDigestIdempotentRepository;
    }

    /**
     * @return when the file was first recorded, or now if there is no record of it
     */
    public LocalDateTime createdAtFor(String fileName) {
        LOGGER.info("Looking up createdAt timestamp for file '{}'", fileName);

        LocalDateTime createdAt = fileNameAndDigestIdempotentRepository.getCreatedAt(fileName);
        if (createdAt != null) {
            LOGGER.info("Found createdAt timestamp {} in idempotent repository for file '{}'", createdAt, fileName);
            return createdAt;
        }
        LocalDateTime now = LocalDateTime.now();
        LOGGER.warn("No createdAt timestamp found in idempotent repository for file '{}', falling back to current time: {}", fileName, now);
        return now;
    }
}

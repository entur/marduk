package no.rutebanken.marduk.routes.file;

import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.repository.FileNameAndDigestIdempotentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.FILE_UPLOADED_TIMESTAMP;

/**
 * Records when a file was first uploaded, from the idempotent repository, as the {@code FileUploadedTimestamp}
 * header. Without a record the header is left unset.
 */
@Component
public class FileUploadedTimestampProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileUploadedTimestampProcessor.class);

    private final FileNameAndDigestIdempotentRepository fileNameAndDigestIdempotentRepository;

    public FileUploadedTimestampProcessor(FileNameAndDigestIdempotentRepository fileNameAndDigestIdempotentRepository) {
        this.fileNameAndDigestIdempotentRepository = fileNameAndDigestIdempotentRepository;
    }

    public void process(MardukMessage message) {
        String fileName = message.getHeader(FILE_NAME, String.class);
        LOGGER.info("Looking up createdAt timestamp for file '{}'", fileName);

        LocalDateTime createdAt = fileNameAndDigestIdempotentRepository.getCreatedAt(fileName);
        if (createdAt == null) {
            LOGGER.warn("No createdAt timestamp found in idempotent repository for file '{}', returning.", fileName);
            return;
        }
        LOGGER.info("Found createdAt timestamp {} in idempotent repository for file '{}'", createdAt, fileName);
        message.setHeader(FILE_UPLOADED_TIMESTAMP, createdAt.toString());
    }
}

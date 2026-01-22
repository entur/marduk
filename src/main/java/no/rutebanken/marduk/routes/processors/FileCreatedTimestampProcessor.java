package no.rutebanken.marduk.routes.processors;

import no.rutebanken.marduk.repository.FileNameAndDigestIdempotentRepository;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.time.LocalDateTime;

import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.FILTERING_FILE_CREATED_TIMESTAMP;

public class FileCreatedTimestampProcessor implements Processor {
    FileNameAndDigestIdempotentRepository fileNameAndDigestIdempotentRepository;

    public FileCreatedTimestampProcessor(FileNameAndDigestIdempotentRepository fileNameAndDigestIdempotentRepository) {
        this.fileNameAndDigestIdempotentRepository = fileNameAndDigestIdempotentRepository;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String fileName = exchange.getIn().getHeader(FILE_NAME, String.class);
        LocalDateTime createdAt = fileNameAndDigestIdempotentRepository.getCreatedAt(fileName);
        if (createdAt != null) {
            // This header should only be set when triggering ashur filtering after pre-validation, and should
            // remain unset for filtering triggered after post-validation because:
            // 1. Filtering after post-validation is a temporary need, and its output will not be used.
            // 2. File names produced by Chouette exports are not unique, and do not exist in marduk's db.
            // 3. A created timestamp should already be set on CompositeFrames produced by Chouette.
            exchange.getIn().setHeader(FILTERING_FILE_CREATED_TIMESTAMP, createdAt.toString());
        }
    }
}
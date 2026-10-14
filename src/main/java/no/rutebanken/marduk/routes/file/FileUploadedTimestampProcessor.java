package no.rutebanken.marduk.routes.file;

import no.rutebanken.marduk.repository.FileNameAndDigestIdempotentRepository;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

import static no.rutebanken.marduk.Constants.FILE_UPLOADED_TIMESTAMP;
import static no.rutebanken.marduk.Constants.FILE_NAME;

public class FileUploadedTimestampProcessor implements Processor{

  private static final Logger LOGGER = LoggerFactory.getLogger(no.rutebanken.marduk.routes.file.FileUploadedTimestampProcessor.class);

  private final FileNameAndDigestIdempotentRepository fileNameAndDigestIdempotentRepository;

  public FileUploadedTimestampProcessor(FileNameAndDigestIdempotentRepository fileNameAndDigestIdempotentRepository) {
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
      LOGGER.warn("No createdAt timestamp found in idempotent repository for file '{}', returning.", fileName);
      return;
    }

    exchange.getIn().setHeader(FILE_UPLOADED_TIMESTAMP, createdAt.toString());
  }

}

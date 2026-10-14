package no.rutebanken.marduk.routes.file;

import no.rutebanken.marduk.repository.FileNameAndDigestIdempotentRepository;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static no.rutebanken.marduk.Constants.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FileUploadedTimestampProcessorTest {

  private Exchange exchange() {
    CamelContext ctx = new DefaultCamelContext();
    return new DefaultExchange(ctx);
  }

  @Test
  void testProcessorUsesTimestampFromRepository() {
    FileNameAndDigestIdempotentRepository repository = mock(FileNameAndDigestIdempotentRepository.class);
    LocalDateTime expectedTimestamp = LocalDateTime.of(2025, 6, 15, 10, 30, 45, 123000000);
    String fileName = "test-file.zip";
    when(repository.getCreatedAt(fileName)).thenReturn(expectedTimestamp);

    FileUploadedTimestampProcessor processor = new FileUploadedTimestampProcessor(repository);
    Exchange exchange = exchange();
    exchange.getIn().setHeader(FILE_NAME, fileName);
    processor.process(exchange);

    String headerValue = exchange.getIn().getHeader(FILE_UPLOADED_TIMESTAMP, String.class);
    Assertions.assertEquals(expectedTimestamp.toString(), headerValue);
  }

  @Test
  void testProcessorFallsBackToNullWhenRepositoryReturnsNull() {
    FileNameAndDigestIdempotentRepository repository = mock(FileNameAndDigestIdempotentRepository.class);
    String fileName = "test-file.zip";
    when(repository.getCreatedAt(fileName)).thenReturn(null);

    FileUploadedTimestampProcessor processor = new FileUploadedTimestampProcessor(repository);
    Exchange exchange = exchange();
    exchange.getIn().setHeader(FILE_NAME, fileName);

    processor.process(exchange);

    String headerValue = exchange.getIn().getHeader(FILE_UPLOADED_TIMESTAMP, String.class);
    Assertions.assertNull(headerValue);
  }
}

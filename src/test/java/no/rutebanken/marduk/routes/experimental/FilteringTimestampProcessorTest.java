package no.rutebanken.marduk.routes.experimental;

import no.rutebanken.marduk.repository.FileNameAndDigestIdempotentRepository;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.FILTERING_FILE_CREATED_TIMESTAMP;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FilteringTimestampProcessorTest {

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

        FilteringTimestampProcessor processor = new FilteringTimestampProcessor(repository);
        Exchange exchange = exchange();
        exchange.getIn().setHeader(FILE_NAME, fileName);
        processor.process(exchange);

        String headerValue = exchange.getIn().getHeader(FILTERING_FILE_CREATED_TIMESTAMP, String.class);
        Assertions.assertEquals(expectedTimestamp.toString(), headerValue);
    }

    @Test
    void testProcessorFallsBackToCurrentTimeWhenRepositoryReturnsNull() {
        FileNameAndDigestIdempotentRepository repository = mock(FileNameAndDigestIdempotentRepository.class);
        String fileName = "test-file.zip";
        when(repository.getCreatedAt(fileName)).thenReturn(null);

        FilteringTimestampProcessor processor = new FilteringTimestampProcessor(repository);
        Exchange exchange = exchange();
        exchange.getIn().setHeader(FILE_NAME, fileName);

        LocalDateTime before = LocalDateTime.now();
        processor.process(exchange);
        LocalDateTime after = LocalDateTime.now();

        String headerValue = exchange.getIn().getHeader(FILTERING_FILE_CREATED_TIMESTAMP, String.class);
        Assertions.assertNotNull(headerValue);
        LocalDateTime actual = LocalDateTime.parse(headerValue);
        Assertions.assertFalse(actual.isBefore(before));
        Assertions.assertFalse(actual.isAfter(after));
    }
}

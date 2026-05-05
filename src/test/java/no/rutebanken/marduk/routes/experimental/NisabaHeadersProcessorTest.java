package no.rutebanken.marduk.routes.experimental;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static no.rutebanken.marduk.Constants.*;

class NisabaHeadersProcessorTest {
    private Exchange exchange() {
        CamelContext ctx = new DefaultCamelContext();
        return new DefaultExchange(ctx);
    }

    @Test
    void testProcessorWithoutTimestampHeader() throws Exception {
        String testContainerName = "nisaba-exchange-bucket";
        NisabaHeadersProcessor processor = new NisabaHeadersProcessor(testContainerName);
        Exchange exchange = exchange();
        exchange.getIn().setHeader(CHOUETTE_REFERENTIAL, "tst");
        processor.process(exchange);

        Assertions.assertEquals(testContainerName, exchange.getIn().getHeader(TARGET_CONTAINER));
        String fileHandle = exchange.getIn().getHeader(TARGET_FILE_HANDLE, String.class);
        Assertions.assertTrue(fileHandle.startsWith("imported/tst/tst_"));
        Assertions.assertTrue(fileHandle.endsWith(".zip"));
    }

    @Test
    void testProcessorUsesTimestampHeader() throws Exception {
        String testContainerName = "nisaba-exchange-bucket";
        NisabaHeadersProcessor processor = new NisabaHeadersProcessor(testContainerName);
        Exchange exchange = exchange();
        exchange.getIn().setHeader(CHOUETTE_REFERENTIAL, "tst");
        LocalDateTime fixedTime = LocalDateTime.of(2025, 6, 15, 10, 30, 45, 123000000);
        exchange.getIn().setHeader(FILTERING_FILE_CREATED_TIMESTAMP, fixedTime.toString());
        processor.process(exchange);

        Assertions.assertEquals(testContainerName, exchange.getIn().getHeader(TARGET_CONTAINER));
        String fileHandle = exchange.getIn().getHeader(TARGET_FILE_HANDLE, String.class);
        Assertions.assertEquals("imported/tst/tst_2025-06-15T10_30_45.123.zip", fileHandle);
    }
}
package no.rutebanken.marduk.routes.experimental;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static no.rutebanken.marduk.Constants.*;

class NisabaHeadersProcessorTest {
    private Exchange exchange() {
        CamelContext ctx = new DefaultCamelContext();
        return new DefaultExchange(ctx);
    }

    @Test
    void testProcessor() throws Exception {
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
}
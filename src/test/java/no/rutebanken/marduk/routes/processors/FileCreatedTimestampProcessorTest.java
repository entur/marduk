package no.rutebanken.marduk.routes.processors;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukSpringBootBaseTest;
import no.rutebanken.marduk.domain.FileNameAndDigest;
import no.rutebanken.marduk.repository.FileNameAndDigestIdempotentRepository;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FileCreatedTimestampProcessorTest extends MardukSpringBootBaseTest {

    @Autowired
    private FileNameAndDigestIdempotentRepository idempotentRepository;

    private Processor processor;

    @BeforeEach
    void setup() {
        FileNameAndDigest fileNameAndDigest = new FileNameAndDigest("existingFile", "digestOne");
        idempotentRepository.add(fileNameAndDigest.toString());
        processor = new FileCreatedTimestampProcessor(idempotentRepository);
    }

    @Test
    void processWhenFileExists() throws Exception {
        CamelContext context = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(Constants.FILE_NAME, "existingFile");
        processor.process(exchange);
        Assertions.assertNotNull(exchange.getIn().getHeader(Constants.FILTERING_FILE_CREATED_TIMESTAMP));
    }

    @Test
    void processWhenFileWasNotFound() throws Exception {
        CamelContext context = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(Constants.FILE_NAME, "unknownFile");
        processor.process(exchange);
        Assertions.assertNull(exchange.getIn().getHeader(Constants.FILTERING_FILE_CREATED_TIMESTAMP));
    }
}
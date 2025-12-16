package no.rutebanken.marduk.routes.processors;

import com.fasterxml.jackson.databind.ObjectWriter;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukSpringBootBaseTest;
import no.rutebanken.marduk.domain.PrevalidatedFileMetadata;
import no.rutebanken.marduk.json.ObjectMapperFactory;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES;
import static no.rutebanken.marduk.Constants.PREVALIDATED_NETEX_METADATA_FILENAME;

class FileCreatedTimestampProcessorTest extends MardukSpringBootBaseTest {

    private static final ObjectWriter OBJECT_WRITER = ObjectMapperFactory.getSharedObjectMapper().writerFor(PrevalidatedFileMetadata.class);

    @Autowired
    private MardukInternalBlobStoreService mardukInternalBlobStoreService;

    private Processor processor;

    @BeforeEach
    void setup() throws Exception {
        super.setUp();
        processor = new FileCreatedTimestampProcessor(mardukInternalBlobStoreService);

        // Create metadata file for testing using the repository directly
        PrevalidatedFileMetadata metadata = new PrevalidatedFileMetadata(
                LocalDateTime.of(2024, 1, 15, 10, 30, 0),
                "original-file.zip"
        );
        String json = OBJECT_WRITER.writeValueAsString(metadata);
        String metadataPath = BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + "existingReferential/" + PREVALIDATED_NETEX_METADATA_FILENAME;

        internalInMemoryBlobStoreRepository.uploadBlob(
                metadataPath,
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))
        );
    }

    @Test
    void processWhenMetadataFileExists() throws Exception {
        CamelContext context = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(Constants.DATASET_REFERENTIAL, "existingReferential");
        processor.process(exchange);
        Assertions.assertNotNull(exchange.getIn().getHeader(Constants.FILTERING_FILE_CREATED_TIMESTAMP));
        Assertions.assertEquals("2024-01-15T10:30", exchange.getIn().getHeader(Constants.FILTERING_FILE_CREATED_TIMESTAMP));
    }

    @Test
    void processWhenMetadataFileWasNotFound() throws Exception {
        CamelContext context = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(Constants.DATASET_REFERENTIAL, "nonExistingReferential");
        processor.process(exchange);
        Assertions.assertNull(exchange.getIn().getHeader(Constants.FILTERING_FILE_CREATED_TIMESTAMP));
    }
}

package no.rutebanken.marduk.routes.processors;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukSpringBootBaseTest;
import no.rutebanken.marduk.domain.FileNameAndDigest;
import no.rutebanken.marduk.domain.PrevalidatedFileMetadata;
import no.rutebanken.marduk.json.ObjectMapperFactory;
import no.rutebanken.marduk.repository.FileNameAndDigestIdempotentRepository;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.time.LocalDateTime;

class FileCreatedTimestampProcessorTest extends MardukSpringBootBaseTest {

    @Autowired
    private FileNameAndDigestIdempotentRepository idempotentRepository;

    @Autowired
    private MardukInternalBlobStoreService mardukInternalBlobStoreService;

    private Processor processor;
    private ObjectMapper objectMapper;
    private CamelContext camelContext;

    @BeforeEach
    void setup() {
        FileNameAndDigest fileNameAndDigest = new FileNameAndDigest("existingFile", "digestOne");
        idempotentRepository.add(fileNameAndDigest.toString());
        processor = new FileCreatedTimestampProcessor(idempotentRepository, mardukInternalBlobStoreService);
        objectMapper = ObjectMapperFactory.getSharedObjectMapper();
        camelContext = new DefaultCamelContext();
    }

    @Test
    void processWhenFileExistsInIdempotentRepository() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader(Constants.FILE_NAME, "existingFile");
        processor.process(exchange);
        Assertions.assertNotNull(exchange.getIn().getHeader(Constants.FILTERING_FILE_CREATED_TIMESTAMP));
    }

    @Test
    void processWhenFileNotFoundInIdempotentRepositoryAndNoMetadata() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader(Constants.FILE_NAME, "unknownFile");
        exchange.getIn().setHeader(Constants.CHOUETTE_REFERENTIAL, "nonexistent");
        processor.process(exchange);
        Assertions.assertNull(exchange.getIn().getHeader(Constants.FILTERING_FILE_CREATED_TIMESTAMP));
    }

    @Test
    void processWhenFileNotFoundButMetadataExists() throws Exception {
        String referential = "rut";
        String fileName = "original.zip";
        LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

        // Create metadata file with matching filename
        PrevalidatedFileMetadata metadata = new PrevalidatedFileMetadata(createdAt, fileName);
        String metadataJson = objectMapper.writeValueAsString(metadata);
        String metadataPath = Constants.BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "/" + Constants.PREVALIDATED_NETEX_METADATA_FILENAME;
        uploadBlob(metadataPath, metadataJson);

        // Process exchange with file not in idempotent repository but matching metadata filename
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader(Constants.FILE_NAME, fileName);
        exchange.getIn().setHeader(Constants.CHOUETTE_REFERENTIAL, referential);
        processor.process(exchange);

        // Verify timestamp was read from metadata
        Assertions.assertEquals(createdAt.toString(), exchange.getIn().getHeader(Constants.FILTERING_FILE_CREATED_TIMESTAMP));
    }

    @Test
    void processWhenFileNotFoundAndMetadataFilenameMismatch() throws Exception {
        String referential = "rut";
        LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

        // Create metadata file with different filename
        PrevalidatedFileMetadata metadata = new PrevalidatedFileMetadata(createdAt, "original.zip");
        String metadataJson = objectMapper.writeValueAsString(metadata);
        String metadataPath = Constants.BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "/" + Constants.PREVALIDATED_NETEX_METADATA_FILENAME;
        uploadBlob(metadataPath, metadataJson);

        // Process exchange with different filename than what's in metadata
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader(Constants.FILE_NAME, "different-file.zip");
        exchange.getIn().setHeader(Constants.CHOUETTE_REFERENTIAL, referential);
        processor.process(exchange);

        // Verify no timestamp is set when filenames don't match
        Assertions.assertNull(exchange.getIn().getHeader(Constants.FILTERING_FILE_CREATED_TIMESTAMP));
    }

    @Test
    void processWhenFileNotFoundAndMetadataHasNullCreatedAt() throws Exception {
        String referential = "rut";
        String fileName = "original.zip";

        // Create metadata file with null createdAt but matching filename
        PrevalidatedFileMetadata metadata = new PrevalidatedFileMetadata(null, fileName);
        String metadataJson = objectMapper.writeValueAsString(metadata);
        String metadataPath = Constants.BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "/" + Constants.PREVALIDATED_NETEX_METADATA_FILENAME;
        uploadBlob(metadataPath, metadataJson);

        // Process exchange
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader(Constants.FILE_NAME, fileName);
        exchange.getIn().setHeader(Constants.CHOUETTE_REFERENTIAL, referential);
        processor.process(exchange);

        // Verify no timestamp is set when metadata has null createdAt
        Assertions.assertNull(exchange.getIn().getHeader(Constants.FILTERING_FILE_CREATED_TIMESTAMP));
    }

    @Test
    void processWhenFileNotFoundAndMalformedMetadata() throws Exception {
        String referential = "rut";

        // Create malformed metadata file
        String metadataPath = Constants.BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "/" + Constants.PREVALIDATED_NETEX_METADATA_FILENAME;
        uploadBlob(metadataPath, "{invalid json}");

        // Process exchange
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader(Constants.FILE_NAME, "unknownFile");
        exchange.getIn().setHeader(Constants.CHOUETTE_REFERENTIAL, referential);
        processor.process(exchange);

        // Verify no timestamp is set when metadata is malformed
        Assertions.assertNull(exchange.getIn().getHeader(Constants.FILTERING_FILE_CREATED_TIMESTAMP));
    }

    @Test
    void processWhenFileNotFoundAndNoReferentialHeader() throws Exception {
        // Process exchange without referential header
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader(Constants.FILE_NAME, "unknownFile");
        // No CHOUETTE_REFERENTIAL header set
        processor.process(exchange);

        // Verify no timestamp is set when referential is missing
        Assertions.assertNull(exchange.getIn().getHeader(Constants.FILTERING_FILE_CREATED_TIMESTAMP));
    }

    private void uploadBlob(String path, String content) {
        InputStream inputStream = IOUtils.toInputStream(content, Charset.defaultCharset());
        internalInMemoryBlobStoreRepository.uploadBlob(path, inputStream);
    }
}
package no.rutebanken.marduk.routes.processors;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.time.LocalDateTime;

class NightlyValidationFileProcessorTest extends MardukSpringBootBaseTest {

    @Autowired
    private MardukInternalBlobStoreService mardukInternalBlobStoreService;

    private Processor processor;
    private ObjectMapper objectMapper;
    private CamelContext camelContext;

    @BeforeEach
    void setup() {
        processor = new NightlyValidationFileProcessor(mardukInternalBlobStoreService);
        objectMapper = ObjectMapperFactory.getSharedObjectMapper();
        camelContext = new DefaultCamelContext();
    }

    @Test
    void testProcessWithExistingMetadataAndOriginalFile() throws Exception {
        String referential = "rut";
        String originalFileName = "testfile.zip";
        LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

        // Create metadata file
        PrevalidatedFileMetadata metadata = new PrevalidatedFileMetadata(createdAt, originalFileName);
        String metadataJson = objectMapper.writeValueAsString(metadata);
        String metadataPath = Constants.BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "/" + Constants.PREVALIDATED_NETEX_METADATA_FILENAME;
        uploadBlob(metadataPath, metadataJson);

        // Create original file
        String originalFilePath = Constants.BLOBSTORE_PATH_INBOUND + referential + "/" + originalFileName;
        uploadBlob(originalFilePath, "original file content");

        // Process exchange
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader(Constants.DATASET_REFERENTIAL, referential);
        processor.process(exchange);

        // Verify headers
        Assertions.assertEquals(originalFilePath, exchange.getIn().getHeader(Constants.FILE_HANDLE));
        Assertions.assertEquals(createdAt.toString(), exchange.getIn().getHeader(Constants.FILTERING_FILE_CREATED_TIMESTAMP));
    }

    @Test
    void testProcessWithExistingMetadataButMissingOriginalFile() throws Exception {
        String referential = "rut";
        String originalFileName = "missing-file.zip";
        LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

        // Create metadata file (but not the original file)
        PrevalidatedFileMetadata metadata = new PrevalidatedFileMetadata(createdAt, originalFileName);
        String metadataJson = objectMapper.writeValueAsString(metadata);
        String metadataPath = Constants.BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "/" + Constants.PREVALIDATED_NETEX_METADATA_FILENAME;
        uploadBlob(metadataPath, metadataJson);

        // Create legacy file
        String legacyFilePath = Constants.BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "-" + Constants.CURRENT_PREVALIDATED_NETEX_FILENAME;
        uploadBlob(legacyFilePath, "legacy file content");

        // Process exchange
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader(Constants.DATASET_REFERENTIAL, referential);
        processor.process(exchange);

        // Verify fallback to legacy file
        Assertions.assertEquals(legacyFilePath, exchange.getIn().getHeader(Constants.FILE_HANDLE));
        Assertions.assertNotNull(exchange.getIn().getHeader(Constants.FILTERING_FILE_CREATED_TIMESTAMP));
    }

    @Test
    void testProcessWithMissingMetadataFile() throws Exception {
        String referential = "rut";

        // Create legacy file only (no metadata file)
        String legacyFilePath = Constants.BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "-" + Constants.CURRENT_PREVALIDATED_NETEX_FILENAME;
        uploadBlob(legacyFilePath, "legacy file content");

        // Process exchange
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader(Constants.DATASET_REFERENTIAL, referential);
        processor.process(exchange);

        // Verify fallback to legacy file
        Assertions.assertEquals(legacyFilePath, exchange.getIn().getHeader(Constants.FILE_HANDLE));
        Assertions.assertNotNull(exchange.getIn().getHeader(Constants.FILTERING_FILE_CREATED_TIMESTAMP));
    }

    @Test
    void testProcessWithMalformedMetadataFile() throws Exception {
        String referential = "rut";

        // Create malformed metadata file
        String metadataPath = Constants.BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "/" + Constants.PREVALIDATED_NETEX_METADATA_FILENAME;
        uploadBlob(metadataPath, "{invalid json}");

        // Create legacy file
        String legacyFilePath = Constants.BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "-" + Constants.CURRENT_PREVALIDATED_NETEX_FILENAME;
        uploadBlob(legacyFilePath, "legacy file content");

        // Process exchange
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader(Constants.DATASET_REFERENTIAL, referential);
        processor.process(exchange);

        // Verify fallback to legacy file
        Assertions.assertEquals(legacyFilePath, exchange.getIn().getHeader(Constants.FILE_HANDLE));
        Assertions.assertNotNull(exchange.getIn().getHeader(Constants.FILTERING_FILE_CREATED_TIMESTAMP));
    }

    @Test
    void testProcessWithNeitherMetadataNorLegacyFile() throws Exception {
        String referential = "nonexistent";

        // Process exchange with no files present
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader(Constants.DATASET_REFERENTIAL, referential);
        processor.process(exchange);

        // Verify no FILE_HANDLE is set
        Assertions.assertNull(exchange.getIn().getHeader(Constants.FILE_HANDLE));
    }

    @Test
    void testProcessWithMetadataButNullOriginalFileName() throws Exception {
        String referential = "rut";
        LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

        // Create metadata file with null originalFileName
        PrevalidatedFileMetadata metadata = new PrevalidatedFileMetadata(createdAt, null);
        String metadataJson = objectMapper.writeValueAsString(metadata);
        String metadataPath = Constants.BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "/" + Constants.PREVALIDATED_NETEX_METADATA_FILENAME;
        uploadBlob(metadataPath, metadataJson);

        // Create legacy file
        String legacyFilePath = Constants.BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "-" + Constants.CURRENT_PREVALIDATED_NETEX_FILENAME;
        uploadBlob(legacyFilePath, "legacy file content");

        // Process exchange
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader(Constants.DATASET_REFERENTIAL, referential);
        processor.process(exchange);

        // Verify fallback to legacy file
        Assertions.assertEquals(legacyFilePath, exchange.getIn().getHeader(Constants.FILE_HANDLE));
        Assertions.assertNotNull(exchange.getIn().getHeader(Constants.FILTERING_FILE_CREATED_TIMESTAMP));
    }

    @Test
    void testProcessWithMetadataButNullCreatedAt() throws Exception {
        String referential = "rut";
        String originalFileName = "testfile.zip";

        // Create metadata file with null createdAt
        PrevalidatedFileMetadata metadata = new PrevalidatedFileMetadata(null, originalFileName);
        String metadataJson = objectMapper.writeValueAsString(metadata);
        String metadataPath = Constants.BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "/" + Constants.PREVALIDATED_NETEX_METADATA_FILENAME;
        uploadBlob(metadataPath, metadataJson);

        // Create original file
        String originalFilePath = Constants.BLOBSTORE_PATH_INBOUND + referential + "/" + originalFileName;
        uploadBlob(originalFilePath, "original file content");

        // Process exchange
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader(Constants.DATASET_REFERENTIAL, referential);
        processor.process(exchange);

        // Verify FILE_HANDLE is set but FILTERING_FILE_CREATED_TIMESTAMP is not
        Assertions.assertEquals(originalFilePath, exchange.getIn().getHeader(Constants.FILE_HANDLE));
        Assertions.assertNull(exchange.getIn().getHeader(Constants.FILTERING_FILE_CREATED_TIMESTAMP));
    }

    private void uploadBlob(String path, String content) {
        InputStream inputStream = IOUtils.toInputStream(content, Charset.defaultCharset());
        internalInMemoryBlobStoreRepository.uploadBlob(path, inputStream);
    }
}

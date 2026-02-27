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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

class PrevalidatedFileMetadataProcessorTest extends MardukSpringBootBaseTest {

    @Autowired
    private FileNameAndDigestIdempotentRepository idempotentRepository;

    @Autowired
    private MardukInternalBlobStoreService mardukInternalBlobStoreService;

    private Processor processor;
    private ObjectMapper objectMapper;
    private CamelContext camelContext;

    @BeforeEach
    void setup() {
        FileNameAndDigest fileNameAndDigest = new FileNameAndDigest("existingFile.zip", "digestOne");
        idempotentRepository.add(fileNameAndDigest.toString());
        processor = new PrevalidatedFileMetadataProcessor(idempotentRepository, mardukInternalBlobStoreService);
        objectMapper = ObjectMapperFactory.getSharedObjectMapper();
        camelContext = new DefaultCamelContext();
    }

    @Test
    void processWhenFileExistsInIdempotentRepository() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader(Constants.FILE_NAME, "existingFile.zip");
        exchange.getIn().setHeader(Constants.CHOUETTE_REFERENTIAL, "rut");

        processor.process(exchange);

        // Verify headers
        Assertions.assertNotNull(exchange.getIn().getHeader(Constants.FILTERING_FILE_CREATED_TIMESTAMP));
        Assertions.assertEquals(
                Constants.BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + "rut/" + Constants.PREVALIDATED_NETEX_METADATA_FILENAME,
                exchange.getIn().getHeader(Constants.FILE_HANDLE)
        );

        // Verify body is an InputStream
        Assertions.assertInstanceOf(ByteArrayInputStream.class, exchange.getIn().getBody());

        // Verify JSON content
        InputStream body = exchange.getIn().getBody(InputStream.class);
        PrevalidatedFileMetadata metadata = objectMapper.readValue(body, PrevalidatedFileMetadata.class);

        Assertions.assertNotNull(metadata.getCreatedAt());
        Assertions.assertEquals("existingFile.zip", metadata.getOriginalFileName());
    }

    @Test
    void processWhenFileNotInDbButExistsInMetadataWithMatchingFilename() throws Exception {
        String referential = "rut";
        String fileName = "newFile.zip";
        LocalDateTime originalCreatedAt = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

        // Create existing metadata file with matching filename
        PrevalidatedFileMetadata existingMetadata = new PrevalidatedFileMetadata(originalCreatedAt, fileName);
        String metadataJson = objectMapper.writeValueAsString(existingMetadata);
        String metadataPath = Constants.BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "/" + Constants.PREVALIDATED_NETEX_METADATA_FILENAME;
        uploadBlob(metadataPath, metadataJson);

        // Process exchange with file not in idempotent repository
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader(Constants.FILE_NAME, fileName);
        exchange.getIn().setHeader(Constants.CHOUETTE_REFERENTIAL, referential);

        processor.process(exchange);

        // Verify the timestamp from existing metadata is used
        Assertions.assertEquals(originalCreatedAt.toString(), exchange.getIn().getHeader(Constants.FILTERING_FILE_CREATED_TIMESTAMP));

        // Verify the output metadata preserves the original timestamp
        InputStream body = exchange.getIn().getBody(InputStream.class);
        PrevalidatedFileMetadata outputMetadata = objectMapper.readValue(body, PrevalidatedFileMetadata.class);
        Assertions.assertEquals(originalCreatedAt, outputMetadata.getCreatedAt());
        Assertions.assertEquals(fileName, outputMetadata.getOriginalFileName());
    }

    @Test
    void processWhenFileNotInDbAndMetadataHasDifferentFilename() throws Exception {
        String referential = "rut";
        LocalDateTime originalCreatedAt = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

        // Create existing metadata file with different filename
        PrevalidatedFileMetadata existingMetadata = new PrevalidatedFileMetadata(originalCreatedAt, "oldFile.zip");
        String metadataJson = objectMapper.writeValueAsString(existingMetadata);
        String metadataPath = Constants.BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "/" + Constants.PREVALIDATED_NETEX_METADATA_FILENAME;
        uploadBlob(metadataPath, metadataJson);

        // Process exchange with different filename
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader(Constants.FILE_NAME, "newFile.zip");
        exchange.getIn().setHeader(Constants.CHOUETTE_REFERENTIAL, referential);

        LocalDateTime beforeProcess = LocalDateTime.now();
        processor.process(exchange);
        LocalDateTime afterProcess = LocalDateTime.now();

        // Verify current time is used (not the original timestamp from metadata)
        String timestampStr = exchange.getIn().getHeader(Constants.FILTERING_FILE_CREATED_TIMESTAMP, String.class);
        LocalDateTime usedTimestamp = LocalDateTime.parse(timestampStr);

        Assertions.assertNotEquals(originalCreatedAt, usedTimestamp);
        Assertions.assertTrue(usedTimestamp.isAfter(beforeProcess.minusSeconds(1)));
        Assertions.assertTrue(usedTimestamp.isBefore(afterProcess.plusSeconds(1)));
    }

    @Test
    void processWhenFileNotInDbAndNoMetadataExists() throws Exception {
        String referential = "nonexistent";

        // Process exchange with no metadata file
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader(Constants.FILE_NAME, "newFile.zip");
        exchange.getIn().setHeader(Constants.CHOUETTE_REFERENTIAL, referential);

        LocalDateTime beforeProcess = LocalDateTime.now();
        processor.process(exchange);
        LocalDateTime afterProcess = LocalDateTime.now();

        // Verify current time is used
        String timestampStr = exchange.getIn().getHeader(Constants.FILTERING_FILE_CREATED_TIMESTAMP, String.class);
        LocalDateTime usedTimestamp = LocalDateTime.parse(timestampStr);

        Assertions.assertTrue(usedTimestamp.isAfter(beforeProcess.minusSeconds(1)));
        Assertions.assertTrue(usedTimestamp.isBefore(afterProcess.plusSeconds(1)));
    }

    @Test
    void processCreatesCorrectMetadataFilePath() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader(Constants.FILE_NAME, "testFile.zip");
        exchange.getIn().setHeader(Constants.CHOUETTE_REFERENTIAL, "testReferential");

        processor.process(exchange);

        String expectedPath = "last-prevalidated-files/testReferential/netex.metadata.json";
        Assertions.assertEquals(expectedPath, exchange.getIn().getHeader(Constants.FILE_HANDLE));
    }

    @Test
    void processCreatesValidJson() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader(Constants.FILE_NAME, "existingFile.zip");
        exchange.getIn().setHeader(Constants.CHOUETTE_REFERENTIAL, "rut");

        processor.process(exchange);

        // Read the JSON from the body
        InputStream body = exchange.getIn().getBody(InputStream.class);
        String jsonString = new String(body.readAllBytes(), StandardCharsets.UTF_8);

        // Verify it's valid JSON containing expected fields
        Assertions.assertTrue(jsonString.contains("\"createdAt\""));
        Assertions.assertTrue(jsonString.contains("\"originalFileName\""));
        Assertions.assertTrue(jsonString.contains("\"existingFile.zip\""));

        // Verify it can be deserialized back to the object
        PrevalidatedFileMetadata metadata = objectMapper.readValue(jsonString, PrevalidatedFileMetadata.class);
        Assertions.assertNotNull(metadata);
    }

    private void uploadBlob(String path, String content) {
        InputStream inputStream = IOUtils.toInputStream(content, Charset.defaultCharset());
        internalInMemoryBlobStoreRepository.uploadBlob(path, inputStream);
    }
}

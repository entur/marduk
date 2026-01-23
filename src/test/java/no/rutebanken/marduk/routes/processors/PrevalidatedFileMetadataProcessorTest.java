package no.rutebanken.marduk.routes.processors;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukSpringBootBaseTest;
import no.rutebanken.marduk.domain.FileNameAndDigest;
import no.rutebanken.marduk.domain.PrevalidatedFileMetadata;
import no.rutebanken.marduk.json.ObjectMapperFactory;
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

class PrevalidatedFileMetadataProcessorTest extends MardukSpringBootBaseTest {

    @Autowired
    private FileNameAndDigestIdempotentRepository idempotentRepository;

    private Processor processor;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        FileNameAndDigest fileNameAndDigest = new FileNameAndDigest("existingFile.zip", "digestOne");
        idempotentRepository.add(fileNameAndDigest.toString());
        processor = new PrevalidatedFileMetadataProcessor(idempotentRepository);
        objectMapper = ObjectMapperFactory.getSharedObjectMapper();
    }

    @Test
    void processWhenFileExists() throws Exception {
        CamelContext context = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(context);
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
    void processCreatesCorrectMetadataFilePath() throws Exception {
        CamelContext context = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(Constants.FILE_NAME, "testFile.zip");
        exchange.getIn().setHeader(Constants.CHOUETTE_REFERENTIAL, "testReferential");

        processor.process(exchange);

        String expectedPath = "last-prevalidated-files/testReferential/netex.metadata.json";
        Assertions.assertEquals(expectedPath, exchange.getIn().getHeader(Constants.FILE_HANDLE));
    }

    @Test
    void processCreatesValidJson() throws Exception {
        CamelContext context = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(context);
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
}

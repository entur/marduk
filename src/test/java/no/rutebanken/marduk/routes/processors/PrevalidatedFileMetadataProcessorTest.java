/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.routes.processors;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.domain.PrevalidatedFileMetadata;
import no.rutebanken.marduk.json.ObjectMapperFactory;
import no.rutebanken.marduk.repository.FileNameAndDigestIdempotentRepository;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static no.rutebanken.marduk.Constants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrevalidatedFileMetadataProcessorTest {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getSharedObjectMapper();

    @Mock
    private FileNameAndDigestIdempotentRepository idempotentRepository;

    private PrevalidatedFileMetadataProcessor processor;

    private CamelContext context;

    @BeforeEach
    void setUp() {
        processor = new PrevalidatedFileMetadataProcessor(idempotentRepository);
        context = new DefaultCamelContext();
    }

    @Test
    void processWhenTimestampFoundInRepository() throws Exception {
        String fileName = "test-file.zip";
        String referential = "testReferential";
        String originalFileHandle = "inbound/received/test-file.zip";
        LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 30, 45);

        when(idempotentRepository.getCreatedAt(fileName)).thenReturn(createdAt);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(FILE_NAME, fileName);
        exchange.getIn().setHeader(CHOUETTE_REFERENTIAL, referential);
        exchange.getIn().setHeader(FILE_HANDLE, originalFileHandle);

        processor.process(exchange);

        InputStream body = exchange.getIn().getBody(InputStream.class);
        assertNotNull(body, "Body should be set");
        String json = new String(body.readAllBytes(), StandardCharsets.UTF_8);
        PrevalidatedFileMetadata metadata = OBJECT_MAPPER.readValue(json, PrevalidatedFileMetadata.class);
        assertEquals(createdAt, metadata.getCreatedAt(), "Metadata should contain correct timestamp");

        String expectedMetadataPath = BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "/" + PREVALIDATED_NETEX_METADATA_FILENAME;
        assertEquals(expectedMetadataPath, exchange.getIn().getHeader(FILE_HANDLE),
            "FILE_HANDLE should be set to metadata file path");

        assertEquals(createdAt.toString(), exchange.getIn().getHeader(FILTERING_FILE_CREATED_TIMESTAMP),
            "FILTERING_FILE_CREATED_TIMESTAMP should be set");

        assertEquals(originalFileHandle, exchange.getProperty("prevalidatedOriginalFileHandle"),
            "Original file handle should be stored in exchange property");

        String expectedTargetNetexPath = BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "/" + referential + "-netex.zip";
        assertEquals(expectedTargetNetexPath, exchange.getProperty("prevalidatedTargetNetexFilePath"),
            "Target netex file path should be stored in exchange property");
    }

    @Test
    void processWhenTimestampNotFoundInRepository() throws Exception {
        String fileName = "unknown-file.zip";
        String referential = "testReferential";
        String originalFileHandle = "inbound/received/unknown-file.zip";

        when(idempotentRepository.getCreatedAt(fileName)).thenReturn(null);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(FILE_NAME, fileName);
        exchange.getIn().setHeader(CHOUETTE_REFERENTIAL, referential);
        exchange.getIn().setHeader(FILE_HANDLE, originalFileHandle);

        processor.process(exchange);

        InputStream body = exchange.getIn().getBody(InputStream.class);
        assertNotNull(body, "Body should be set");
        String json = new String(body.readAllBytes(), StandardCharsets.UTF_8);
        PrevalidatedFileMetadata metadata = OBJECT_MAPPER.readValue(json, PrevalidatedFileMetadata.class);

        assertNotNull(metadata.getCreatedAt(), "Metadata should have a timestamp");

        String expectedMetadataPath = BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "/" + PREVALIDATED_NETEX_METADATA_FILENAME;
        assertEquals(expectedMetadataPath, exchange.getIn().getHeader(FILE_HANDLE),
            "FILE_HANDLE should be set to metadata file path");

        assertNotNull(exchange.getIn().getHeader(FILTERING_FILE_CREATED_TIMESTAMP),
            "FILTERING_FILE_CREATED_TIMESTAMP should be set");

        assertEquals(originalFileHandle, exchange.getProperty("prevalidatedOriginalFileHandle"),
            "Original file handle should be stored in exchange property");

        String expectedTargetNetexPath = BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "/" + referential + "-netex.zip";
        assertEquals(expectedTargetNetexPath, exchange.getProperty("prevalidatedTargetNetexFilePath"),
            "Target netex file path should be stored in exchange property");
    }

    @Test
    void processWithDifferentReferential() throws Exception {
        String fileName = "another-file.zip";
        String referential = "anotherRef";
        String originalFileHandle = "inbound/received/another-file.zip";
        LocalDateTime createdAt = LocalDateTime.of(2024, 6, 20, 14, 15, 30);

        when(idempotentRepository.getCreatedAt(fileName)).thenReturn(createdAt);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(FILE_NAME, fileName);
        exchange.getIn().setHeader(CHOUETTE_REFERENTIAL, referential);
        exchange.getIn().setHeader(FILE_HANDLE, originalFileHandle);

        processor.process(exchange);

        String expectedMetadataPath = BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "/" + PREVALIDATED_NETEX_METADATA_FILENAME;
        assertEquals(expectedMetadataPath, exchange.getIn().getHeader(FILE_HANDLE),
            "FILE_HANDLE should use correct referential in path");

        String expectedTargetNetexPath = BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "/" + referential + "-netex.zip";
        assertEquals(expectedTargetNetexPath, exchange.getProperty("prevalidatedTargetNetexFilePath"),
            "Target netex file path should use correct referential");
    }

    @Test
    void processVerifiesJsonFormat() throws Exception {
        String fileName = "test-file.zip";
        String referential = "testRef";
        String originalFileHandle = "inbound/received/test-file.zip";
        LocalDateTime createdAt = LocalDateTime.of(2024, 3, 10, 8, 45, 0);

        when(idempotentRepository.getCreatedAt(fileName)).thenReturn(createdAt);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(FILE_NAME, fileName);
        exchange.getIn().setHeader(CHOUETTE_REFERENTIAL, referential);
        exchange.getIn().setHeader(FILE_HANDLE, originalFileHandle);

        processor.process(exchange);

        InputStream body = exchange.getIn().getBody(InputStream.class);
        String json = new String(body.readAllBytes(), StandardCharsets.UTF_8);

        assertTrue(json.contains("\"createdAt\""), "JSON should contain createdAt field");
        assertTrue(json.contains("[2024,3,10,8,45]"), "JSON should contain timestamp as array [year,month,day,hour,minute]");

        PrevalidatedFileMetadata deserializedMetadata = OBJECT_MAPPER.readValue(json, PrevalidatedFileMetadata.class);
        assertEquals(createdAt, deserializedMetadata.getCreatedAt(), "Deserialized metadata should match original timestamp");
    }
}

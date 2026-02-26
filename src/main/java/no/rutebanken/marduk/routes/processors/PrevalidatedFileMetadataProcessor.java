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

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import no.rutebanken.marduk.domain.PrevalidatedFileMetadata;
import no.rutebanken.marduk.json.ObjectMapperFactory;
import no.rutebanken.marduk.repository.FileNameAndDigestIdempotentRepository;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static no.rutebanken.marduk.Constants.*;

/**
 * Processor that creates metadata for a prevalidated NeTEx file.
 * <p>
 * This processor:
 * 1. Looks up the createdAt timestamp from the idempotent repository using the original filename
 * 2. Creates a PrevalidatedFileMetadata object with the timestamp and original filename
 * 3. Serializes it to JSON and sets it as the exchange body (as InputStream)
 * 4. Sets FILE_HANDLE to the metadata file path in last-prevalidated-files/{referential}/
 * <p>
 * After this processor runs, the route can call direct:uploadInternalBlob to write the metadata file.
 */
public class PrevalidatedFileMetadataProcessor implements Processor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrevalidatedFileMetadataProcessor.class);
    private static final ObjectWriter OBJECT_WRITER = ObjectMapperFactory.getSharedObjectMapper().writerFor(PrevalidatedFileMetadata.class);
    private static final ObjectReader OBJECT_READER = ObjectMapperFactory.getSharedObjectMapper().readerFor(PrevalidatedFileMetadata.class);

    private final FileNameAndDigestIdempotentRepository fileNameAndDigestIdempotentRepository;
    private final MardukInternalBlobStoreService mardukInternalBlobStoreService;

    public PrevalidatedFileMetadataProcessor(
            FileNameAndDigestIdempotentRepository fileNameAndDigestIdempotentRepository,
            MardukInternalBlobStoreService mardukInternalBlobStoreService) {
        this.fileNameAndDigestIdempotentRepository = fileNameAndDigestIdempotentRepository;
        this.mardukInternalBlobStoreService = mardukInternalBlobStoreService;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String originalFileName = exchange.getIn().getHeader(FILE_NAME, String.class);
        String referential = exchange.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class);

        LocalDateTime createdAt = fileNameAndDigestIdempotentRepository.getCreatedAt(originalFileName);

        if (createdAt == null) {
            createdAt = readCreatedAtFromExistingMetadata(referential, originalFileName);
        }

        if (createdAt == null) {
            LOGGER.warn("No createdAt timestamp found for file {}, using current time", originalFileName);
            createdAt = LocalDateTime.now();
        }

        PrevalidatedFileMetadata metadata = new PrevalidatedFileMetadata(createdAt, originalFileName);
        String json = OBJECT_WRITER.writeValueAsString(metadata);

        exchange.getIn().setBody(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        String metadataFilePath = BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "/" + PREVALIDATED_NETEX_METADATA_FILENAME;
        exchange.getIn().setHeader(FILE_HANDLE, metadataFilePath);
        exchange.getIn().setHeader(FILTERING_FILE_CREATED_TIMESTAMP, createdAt.toString());

        LOGGER.info("Prepared metadata for prevalidated file. Original filename: {}, createdAt: {}, metadata path: {}",
                originalFileName, createdAt, metadataFilePath);
    }

    private LocalDateTime readCreatedAtFromExistingMetadata(String referential, String fileName) {
        if (referential == null) {
            return null;
        }
        String metadataFilePath = BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES
                + referential + "/" + PREVALIDATED_NETEX_METADATA_FILENAME;

        try (InputStream inputStream = mardukInternalBlobStoreService.getBlob(metadataFilePath)) {
            if (inputStream == null) {
                LOGGER.info("Existing metadata file not found: {}", metadataFilePath);
                return null;
            }
            PrevalidatedFileMetadata metadata = OBJECT_READER.readValue(inputStream);

            if (!fileName.equals(metadata.getOriginalFileName())) {
                LOGGER.info("Filename mismatch in existing metadata: expected {}, found {}",
                        fileName, metadata.getOriginalFileName());
                return null;
            }

            LOGGER.info("Read createdAt from existing metadata file: {}", metadata.getCreatedAt());
            return metadata.getCreatedAt();
        } catch (Exception e) {
            LOGGER.warn("Failed to read existing metadata file: {}", metadataFilePath, e);
            return null;
        }
    }
}

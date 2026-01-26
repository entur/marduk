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
import no.rutebanken.marduk.domain.PrevalidatedFileMetadata;
import no.rutebanken.marduk.json.ObjectMapperFactory;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.time.LocalDateTime;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_INBOUND;
import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES;
import static no.rutebanken.marduk.Constants.CURRENT_PREVALIDATED_NETEX_FILENAME;
import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILTERING_FILE_CREATED_TIMESTAMP;
import static no.rutebanken.marduk.Constants.PREVALIDATED_NETEX_METADATA_FILENAME;

/**
 * Processor that retrieves metadata for the last prevalidated file for a codespace.
 * Used specifically for nightly validation to locate the original uploaded file.
 * <p>
 * This processor reads a metadata JSON file from blob storage and:
 * <ul>
 *   <li>Sets the FILTERING_FILE_CREATED_TIMESTAMP header with the original upload timestamp</li>
 *   <li>Sets the FILE_HANDLE header to point to the original file in the inbound directory,
 *       if the file still exists</li>
 * </ul>
 * <p>
 * If the metadata file does not exist or the original file cannot be found, this processor
 * falls back to using the legacy preprocessed file at last-prevalidated-files/{codespace}-netex.zip
 * with the current timestamp.
 */
public class NightlyValidationFileProcessor implements Processor {

    private static final Logger LOGGER = LoggerFactory.getLogger(NightlyValidationFileProcessor.class);
    private static final ObjectReader OBJECT_READER = ObjectMapperFactory.getSharedObjectMapper().readerFor(PrevalidatedFileMetadata.class);

    private final MardukInternalBlobStoreService mardukInternalBlobStoreService;

    public NightlyValidationFileProcessor(MardukInternalBlobStoreService mardukInternalBlobStoreService) {
        this.mardukInternalBlobStoreService = mardukInternalBlobStoreService;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String referential = exchange.getIn().getHeader(DATASET_REFERENTIAL, String.class);
        String metadataFilePath = BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "/" + PREVALIDATED_NETEX_METADATA_FILENAME;

        LOGGER.info("Reading metadata from file: {}", metadataFilePath);

        boolean metadataProcessed = false;

        try (InputStream inputStream = mardukInternalBlobStoreService.getBlob(metadataFilePath)) {
            if (inputStream != null) {
                PrevalidatedFileMetadata metadata = OBJECT_READER.readValue(inputStream);
                LocalDateTime createdAt = metadata.getCreatedAt();
                String originalFileName = metadata.getOriginalFileName();

                LOGGER.info("Read metadata from file. Original filename: {}, createdAt: {}",
                        originalFileName, createdAt);

                if (originalFileName != null) {
                    String originalFilePath = BLOBSTORE_PATH_INBOUND + referential + "/" + originalFileName;
                    if (mardukInternalBlobStoreService.blobExists(originalFilePath)) {
                        exchange.getIn().setHeader(FILE_HANDLE, originalFilePath);
                        if (createdAt != null) {
                            exchange.getIn().setHeader(FILTERING_FILE_CREATED_TIMESTAMP, createdAt.toString());
                        }
                        LOGGER.info("Set FILE_HANDLE to original file path: {}", originalFilePath);
                        metadataProcessed = true;
                    } else {
                        LOGGER.warn("Original file not found at path: {}. Falling back to legacy file.", originalFilePath);
                    }
                }
            } else {
                LOGGER.warn("Metadata file not found: {}. Falling back to legacy file.", metadataFilePath);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read metadata file: {}. Falling back to legacy file.", metadataFilePath, e);
        }

        // Fallback to legacy file if metadata processing failed or file not found
        if (!metadataProcessed) {
            String legacyFilePath = BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "-" + CURRENT_PREVALIDATED_NETEX_FILENAME;
            if (mardukInternalBlobStoreService.blobExists(legacyFilePath)) {
                exchange.getIn().setHeader(FILE_HANDLE, legacyFilePath);
                exchange.getIn().setHeader(FILTERING_FILE_CREATED_TIMESTAMP, LocalDateTime.now().toString());
                LOGGER.info("Using legacy prevalidated file: {}", legacyFilePath);
            } else {
                LOGGER.error("Neither metadata-based file nor legacy file found for referential: {}", referential);
            }
        }
    }
}

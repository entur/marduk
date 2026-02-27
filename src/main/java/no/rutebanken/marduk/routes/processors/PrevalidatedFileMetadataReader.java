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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.time.LocalDateTime;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES;
import static no.rutebanken.marduk.Constants.PREVALIDATED_NETEX_METADATA_FILENAME;

/**
 * Helper class for reading createdAt timestamps from prevalidated file metadata stored in blob storage.
 * Used as a fallback when the timestamp is not available in the idempotent repository.
 */
public class PrevalidatedFileMetadataReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrevalidatedFileMetadataReader.class);
    private static final ObjectReader OBJECT_READER = ObjectMapperFactory.getSharedObjectMapper()
            .readerFor(PrevalidatedFileMetadata.class);

    private final MardukInternalBlobStoreService mardukInternalBlobStoreService;

    public PrevalidatedFileMetadataReader(MardukInternalBlobStoreService mardukInternalBlobStoreService) {
        this.mardukInternalBlobStoreService = mardukInternalBlobStoreService;
    }

    /**
     * Reads the createdAt timestamp from the metadata file in blob storage.
     * Only returns a value if the metadata file exists and the filename in the metadata matches the expected filename.
     *
     * @param referential the referential/codespace to look up
     * @param expectedFileName the expected original filename that must match the metadata
     * @return the createdAt timestamp from the metadata, or null if not found or filename doesn't match
     */
    public LocalDateTime readCreatedAt(String referential, String expectedFileName) {
        if (referential == null) {
            return null;
        }
        String metadataFilePath = BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES
                + referential + "/" + PREVALIDATED_NETEX_METADATA_FILENAME;

        try (InputStream inputStream = mardukInternalBlobStoreService.getBlob(metadataFilePath)) {
            if (inputStream == null) {
                LOGGER.info("Metadata file not found: {}", metadataFilePath);
                return null;
            }
            PrevalidatedFileMetadata metadata = OBJECT_READER.readValue(inputStream);

            if (!expectedFileName.equals(metadata.getOriginalFileName())) {
                LOGGER.info("Filename mismatch: expected {}, but metadata contains {}",
                        expectedFileName, metadata.getOriginalFileName());
                return null;
            }

            LOGGER.info("Read createdAt from metadata file: {}", metadata.getCreatedAt());
            return metadata.getCreatedAt();
        } catch (Exception e) {
            LOGGER.warn("Failed to read metadata file: {}", metadataFilePath, e);
            return null;
        }
    }
}

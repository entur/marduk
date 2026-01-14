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

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES;
import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILTERING_FILE_CREATED_TIMESTAMP;
import static no.rutebanken.marduk.Constants.PREVALIDATED_NETEX_METADATA_FILENAME;

/**
 * Processor that retrieves metadata for the last prevalidated file for a codespace.
 * <p>
 * This processor reads a metadata JSON file from blob storage and:
 * <ul>
 *   <li>Sets the FILTERING_FILE_CREATED_TIMESTAMP header with the original upload timestamp</li>
 *   <li>Sets the FILE_HANDLE header to point to the prevalidated file in
 *       last-prevalidated-files/{referential}/{referential}-netex.zip, if the file exists</li>
 * </ul>
 * <p>
 * This is used for nightly validation to validate the last prevalidated file
 */
public class FileCreatedTimestampProcessor implements Processor {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileCreatedTimestampProcessor.class);
    private static final ObjectReader OBJECT_READER = ObjectMapperFactory.getSharedObjectMapper().readerFor(PrevalidatedFileMetadata.class);

    private final MardukInternalBlobStoreService mardukInternalBlobStoreService;

    public FileCreatedTimestampProcessor(MardukInternalBlobStoreService mardukInternalBlobStoreService) {
        this.mardukInternalBlobStoreService = mardukInternalBlobStoreService;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String referential = exchange.getIn().getHeader(DATASET_REFERENTIAL, String.class);
        String metadataFilePath = BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "/" + PREVALIDATED_NETEX_METADATA_FILENAME;

        LOGGER.info("Reading metadata from file: {}", metadataFilePath);

        try (InputStream inputStream = mardukInternalBlobStoreService.getBlob(metadataFilePath)) {
            if (inputStream == null) {
                LOGGER.warn("Metadata file not found: {}", metadataFilePath);
                return;
            }

            PrevalidatedFileMetadata metadata = OBJECT_READER.readValue(inputStream);
            LocalDateTime createdAt = metadata.getCreatedAt();

            LOGGER.info("Read metadata from file. createdAt: {}", createdAt);

            if (createdAt != null) {
                exchange.getIn().setHeader(FILTERING_FILE_CREATED_TIMESTAMP, createdAt.toString());
            }

            String prevalidatedFilePath = BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "/" + referential + "-netex.zip";
            if (Boolean.TRUE.equals(mardukInternalBlobStoreService.blobExists(prevalidatedFilePath))) {
                exchange.getIn().setHeader(FILE_HANDLE, prevalidatedFilePath);
                LOGGER.info("Set FILE_HANDLE to prevalidated file path: {}", prevalidatedFilePath);
            } else {
                LOGGER.warn("Prevalidated file not found at path: {}. FILE_HANDLE not set.", prevalidatedFilePath);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read metadata file: {}", metadataFilePath, e);
        }
    }
}

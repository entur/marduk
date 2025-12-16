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
import static no.rutebanken.marduk.Constants.FILTERING_FILE_CREATED_TIMESTAMP;
import static no.rutebanken.marduk.Constants.PREVALIDATED_NETEX_METADATA_FILENAME;

/**
 * Processor that retrieves the createdAt timestamp for a file and sets it as a header.
 * <p>
 * The timestamp is read from a metadata JSON file stored in blob storage alongside
 * the prevalidated NeTEx file. This provides a single source of truth for both
 * normal imports and nightly validation runs.
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

        LOGGER.info("Reading createdAt timestamp from metadata file: {}", metadataFilePath);

        try (InputStream inputStream = mardukInternalBlobStoreService.getBlob(metadataFilePath)) {
            if (inputStream == null) {
                LOGGER.warn("Metadata file not found: {}", metadataFilePath);
                return;
            }

            PrevalidatedFileMetadata metadata = OBJECT_READER.readValue(inputStream);
            LocalDateTime createdAt = metadata.getCreatedAt();

            LOGGER.info("Read metadata from file. Original filename: {}, createdAt: {}",
                    metadata.getOriginalFileName(), createdAt);

            if (createdAt != null) {
                exchange.getIn().setHeader(FILTERING_FILE_CREATED_TIMESTAMP, createdAt.toString());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read metadata file: {}", metadataFilePath, e);
        }
    }
}

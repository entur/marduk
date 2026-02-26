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
import no.rutebanken.marduk.repository.FileNameAndDigestIdempotentRepository;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.time.LocalDateTime;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.FILTERING_FILE_CREATED_TIMESTAMP;
import static no.rutebanken.marduk.Constants.PREVALIDATED_NETEX_METADATA_FILENAME;

public class FileCreatedTimestampProcessor implements Processor {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileCreatedTimestampProcessor.class);
    private static final ObjectReader OBJECT_READER = ObjectMapperFactory.getSharedObjectMapper()
            .readerFor(PrevalidatedFileMetadata.class);

    private final FileNameAndDigestIdempotentRepository fileNameAndDigestIdempotentRepository;
    private final MardukInternalBlobStoreService mardukInternalBlobStoreService;

    public FileCreatedTimestampProcessor(
            FileNameAndDigestIdempotentRepository fileNameAndDigestIdempotentRepository,
            MardukInternalBlobStoreService mardukInternalBlobStoreService) {
        this.fileNameAndDigestIdempotentRepository = fileNameAndDigestIdempotentRepository;
        this.mardukInternalBlobStoreService = mardukInternalBlobStoreService;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String fileName = exchange.getIn().getHeader(FILE_NAME, String.class);
        LocalDateTime createdAt = fileNameAndDigestIdempotentRepository.getCreatedAt(fileName);

        if (createdAt == null) {
            String referential = exchange.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class);
            createdAt = readCreatedAtFromMetadata(referential, fileName);
        }

        if (createdAt != null) {
            exchange.getIn().setHeader(FILTERING_FILE_CREATED_TIMESTAMP, createdAt.toString());
        }
    }

    private LocalDateTime readCreatedAtFromMetadata(String referential, String fileName) {
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

            if (!fileName.equals(metadata.getOriginalFileName())) {
                LOGGER.info("Filename mismatch: expected {}, but metadata contains {}",
                        fileName, metadata.getOriginalFileName());
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
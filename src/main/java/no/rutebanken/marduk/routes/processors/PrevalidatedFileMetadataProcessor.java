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

import com.fasterxml.jackson.databind.ObjectWriter;
import no.rutebanken.marduk.domain.PrevalidatedFileMetadata;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.json.ObjectMapperFactory;
import no.rutebanken.marduk.repository.FileNameAndDigestIdempotentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES;
import static no.rutebanken.marduk.Constants.PREVALIDATED_NETEX_METADATA_FILENAME;

/**
 * Describes the metadata file written for a dataset that has just passed pre-validation.
 *
 * <p>The file records when the dataset was first received and under which name, which is what
 * {@link NightlyValidationFileProcessor} reads to find the original file again the following night.
 */
public class PrevalidatedFileMetadataProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrevalidatedFileMetadataProcessor.class);
    private static final ObjectWriter OBJECT_WRITER = ObjectMapperFactory.getSharedObjectMapper().writerFor(PrevalidatedFileMetadata.class);

    /** Where the metadata file goes, when the dataset arrived, and the JSON to write. */
    public record Metadata(String fileHandle, LocalDateTime createdAt, String json) {
    }

    private final FileNameAndDigestIdempotentRepository fileNameAndDigestIdempotentRepository;

    public PrevalidatedFileMetadataProcessor(FileNameAndDigestIdempotentRepository fileNameAndDigestIdempotentRepository) {
        this.fileNameAndDigestIdempotentRepository = fileNameAndDigestIdempotentRepository;
    }

    /**
     * @param originalFileName the name the dataset was uploaded under, which is also the idempotency key
     * @param referential      the Chouette referential the metadata file is filed under
     */
    public Metadata describe(String originalFileName, String referential) {
        LocalDateTime createdAt = fileNameAndDigestIdempotentRepository.getCreatedAt(originalFileName);
        if (createdAt == null) {
            LOGGER.warn("No createdAt timestamp found for file {}, using current time", originalFileName);
            createdAt = LocalDateTime.now();
        }

        String fileHandle = BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "/" + PREVALIDATED_NETEX_METADATA_FILENAME;
        LOGGER.info("Prepared metadata for prevalidated file. Original filename: {}, createdAt: {}, metadata path: {}",
                originalFileName, createdAt, fileHandle);
        return new Metadata(fileHandle, createdAt, toJson(new PrevalidatedFileMetadata(createdAt, originalFileName)));
    }

    private static String toJson(PrevalidatedFileMetadata metadata) {
        try {
            return OBJECT_WRITER.writeValueAsString(metadata);
        } catch (IOException e) {
            throw new MardukException("Could not serialise the prevalidated file metadata", e);
        }
    }
}

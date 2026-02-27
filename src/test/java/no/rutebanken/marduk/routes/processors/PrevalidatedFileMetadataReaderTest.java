package no.rutebanken.marduk.routes.processors;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukSpringBootBaseTest;
import no.rutebanken.marduk.domain.PrevalidatedFileMetadata;
import no.rutebanken.marduk.json.ObjectMapperFactory;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.time.LocalDateTime;

class PrevalidatedFileMetadataReaderTest extends MardukSpringBootBaseTest {

    @Autowired
    private MardukInternalBlobStoreService mardukInternalBlobStoreService;

    private PrevalidatedFileMetadataReader reader;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        reader = new PrevalidatedFileMetadataReader(mardukInternalBlobStoreService);
        objectMapper = ObjectMapperFactory.getSharedObjectMapper();
    }

    @Test
    void readCreatedAtWhenMetadataExistsAndFilenameMatches() throws Exception {
        String referential = "rut";
        String fileName = "original.zip";
        LocalDateTime expectedCreatedAt = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

        PrevalidatedFileMetadata metadata = new PrevalidatedFileMetadata(expectedCreatedAt, fileName);
        String metadataJson = objectMapper.writeValueAsString(metadata);
        String metadataPath = Constants.BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "/" + Constants.PREVALIDATED_NETEX_METADATA_FILENAME;
        uploadBlob(metadataPath, metadataJson);

        LocalDateTime result = reader.readCreatedAt(referential, fileName);

        Assertions.assertEquals(expectedCreatedAt, result);
    }

    @Test
    void readCreatedAtReturnsNullWhenReferentialIsNull() {
        LocalDateTime result = reader.readCreatedAt(null, "someFile.zip");

        Assertions.assertNull(result);
    }

    @Test
    void readCreatedAtReturnsNullWhenMetadataFileDoesNotExist() {
        LocalDateTime result = reader.readCreatedAt("nonexistent", "someFile.zip");

        Assertions.assertNull(result);
    }

    @Test
    void readCreatedAtReturnsNullWhenFilenameMismatch() throws Exception {
        String referential = "rut";
        LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

        PrevalidatedFileMetadata metadata = new PrevalidatedFileMetadata(createdAt, "original.zip");
        String metadataJson = objectMapper.writeValueAsString(metadata);
        String metadataPath = Constants.BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "/" + Constants.PREVALIDATED_NETEX_METADATA_FILENAME;
        uploadBlob(metadataPath, metadataJson);

        LocalDateTime result = reader.readCreatedAt(referential, "different-file.zip");

        Assertions.assertNull(result);
    }

    @Test
    void readCreatedAtReturnsNullWhenMetadataIsMalformed() {
        String referential = "rut";

        String metadataPath = Constants.BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "/" + Constants.PREVALIDATED_NETEX_METADATA_FILENAME;
        uploadBlob(metadataPath, "{invalid json}");

        LocalDateTime result = reader.readCreatedAt(referential, "someFile.zip");

        Assertions.assertNull(result);
    }

    @Test
    void readCreatedAtReturnsNullWhenMetadataHasNullCreatedAt() throws Exception {
        String referential = "rut";
        String fileName = "original.zip";

        PrevalidatedFileMetadata metadata = new PrevalidatedFileMetadata(null, fileName);
        String metadataJson = objectMapper.writeValueAsString(metadata);
        String metadataPath = Constants.BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "/" + Constants.PREVALIDATED_NETEX_METADATA_FILENAME;
        uploadBlob(metadataPath, metadataJson);

        LocalDateTime result = reader.readCreatedAt(referential, fileName);

        Assertions.assertNull(result);
    }

    private void uploadBlob(String path, String content) {
        InputStream inputStream = IOUtils.toInputStream(content, Charset.defaultCharset());
        internalInMemoryBlobStoreRepository.uploadBlob(path, inputStream);
    }
}

package no.rutebanken.marduk.routes.processors;

import no.rutebanken.marduk.domain.PrevalidatedFileMetadata;
import no.rutebanken.marduk.json.ObjectMapperFactory;
import no.rutebanken.marduk.repository.FileNameAndDigestIdempotentRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrevalidatedFileMetadataProcessorTest {

    private static final LocalDateTime RECEIVED_AT = LocalDateTime.of(2026, 3, 27, 12, 0, 0);

    private final FileNameAndDigestIdempotentRepository idempotentRepository =
            mock(FileNameAndDigestIdempotentRepository.class);
    private final PrevalidatedFileMetadataProcessor processor =
            new PrevalidatedFileMetadataProcessor(idempotentRepository);

    @Test
    void theMetadataRecordsWhenTheFileWasFirstReceivedAndUnderWhichName() throws Exception {
        when(idempotentRepository.getCreatedAt("existingFile.zip")).thenReturn(RECEIVED_AT);

        PrevalidatedFileMetadataProcessor.Metadata metadata = processor.describe("existingFile.zip", "rut");

        assertEquals(RECEIVED_AT, metadata.createdAt());
        PrevalidatedFileMetadata written = ObjectMapperFactory.getSharedObjectMapper()
                .readValue(metadata.json(), PrevalidatedFileMetadata.class);
        assertEquals(RECEIVED_AT, written.getCreatedAt());
        assertEquals("existingFile.zip", written.getOriginalFileName());
    }

    @Test
    void theMetadataIsFiledUnderTheReferentialSoTheNightlyValidationFindsIt() {
        when(idempotentRepository.getCreatedAt("testFile.zip")).thenReturn(RECEIVED_AT);

        PrevalidatedFileMetadataProcessor.Metadata metadata = processor.describe("testFile.zip", "testReferential");

        assertEquals("last-prevalidated-files/testReferential/netex.metadata.json", metadata.fileHandle());
    }

    @Test
    void aFileWithNoRecordedArrivalFallsBackToNow() {
        when(idempotentRepository.getCreatedAt("unknown.zip")).thenReturn(null);

        LocalDateTime before = LocalDateTime.now();
        PrevalidatedFileMetadataProcessor.Metadata metadata = processor.describe("unknown.zip", "rut");
        LocalDateTime after = LocalDateTime.now();

        assertFalse(metadata.createdAt().isBefore(before));
        assertFalse(metadata.createdAt().isAfter(after));
        assertTrue(metadata.json().contains("\"createdAt\""));
    }
}

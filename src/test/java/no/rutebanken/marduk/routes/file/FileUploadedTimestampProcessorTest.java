package no.rutebanken.marduk.routes.file;

import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.repository.FileNameAndDigestIdempotentRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.FILE_UPLOADED_TIMESTAMP;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileUploadedTimestampProcessorTest {

    @Test
    void testProcessorUsesTimestampFromRepository() {
        FileNameAndDigestIdempotentRepository repository = mock(FileNameAndDigestIdempotentRepository.class);
        LocalDateTime expectedTimestamp = LocalDateTime.of(2025, 6, 15, 10, 30, 45, 123000000);
        String fileName = "test-file.zip";
        when(repository.getCreatedAt(fileName)).thenReturn(expectedTimestamp);

        MardukMessage message = new MardukMessage().setHeader(FILE_NAME, fileName);
        new FileUploadedTimestampProcessor(repository).process(message);

        Assertions.assertEquals(expectedTimestamp.toString(), message.getHeader(FILE_UPLOADED_TIMESTAMP, String.class));
    }

    @Test
    void testProcessorFallsBackToNullWhenRepositoryReturnsNull() {
        FileNameAndDigestIdempotentRepository repository = mock(FileNameAndDigestIdempotentRepository.class);
        String fileName = "test-file.zip";
        when(repository.getCreatedAt(fileName)).thenReturn(null);

        MardukMessage message = new MardukMessage().setHeader(FILE_NAME, fileName);
        new FileUploadedTimestampProcessor(repository).process(message);

        Assertions.assertNull(message.getHeader(FILE_UPLOADED_TIMESTAMP, String.class));
    }
}

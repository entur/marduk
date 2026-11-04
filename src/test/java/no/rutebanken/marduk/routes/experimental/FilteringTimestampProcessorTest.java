package no.rutebanken.marduk.routes.experimental;

import no.rutebanken.marduk.repository.FileNameAndDigestIdempotentRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FilteringTimestampProcessorTest {

    @Test
    void testProcessorUsesTimestampFromRepository() {
        FileNameAndDigestIdempotentRepository repository = mock(FileNameAndDigestIdempotentRepository.class);
        LocalDateTime expectedTimestamp = LocalDateTime.of(2025, 6, 15, 10, 30, 45, 123000000);
        String fileName = "test-file.zip";
        when(repository.getCreatedAt(fileName)).thenReturn(expectedTimestamp);

        FilteringTimestampProcessor processor = new FilteringTimestampProcessor(repository);

        Assertions.assertEquals(expectedTimestamp, processor.createdAtFor(fileName));
    }

    @Test
    void testProcessorFallsBackToCurrentTimeWhenRepositoryReturnsNull() {
        FileNameAndDigestIdempotentRepository repository = mock(FileNameAndDigestIdempotentRepository.class);
        String fileName = "test-file.zip";
        when(repository.getCreatedAt(fileName)).thenReturn(null);

        FilteringTimestampProcessor processor = new FilteringTimestampProcessor(repository);

        LocalDateTime before = LocalDateTime.now();
        LocalDateTime actual = processor.createdAtFor(fileName);
        LocalDateTime after = LocalDateTime.now();

        Assertions.assertNotNull(actual);
        Assertions.assertFalse(actual.isBefore(before));
        Assertions.assertFalse(actual.isAfter(after));
    }
}

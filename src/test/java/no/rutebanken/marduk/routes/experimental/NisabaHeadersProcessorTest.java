package no.rutebanken.marduk.routes.experimental;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

class NisabaHeadersProcessorTest {

    private static final String CONTAINER = "nisaba-exchange-bucket";

    private final NisabaHeadersProcessor processor = new NisabaHeadersProcessor(CONTAINER);

    @Test
    void testProcessorWithoutTimestampHeader() {
        NisabaHeadersProcessor.UploadTarget target = processor.targetFor("tst", null);

        Assertions.assertEquals(CONTAINER, target.container());
        Assertions.assertTrue(target.fileHandle().startsWith("imported/tst/tst_"));
        Assertions.assertTrue(target.fileHandle().endsWith(".zip"));
    }

    @Test
    void testProcessorUsesTimestampHeader() {
        LocalDateTime fixedTime = LocalDateTime.of(2025, 6, 15, 10, 30, 45, 123000000);

        NisabaHeadersProcessor.UploadTarget target = processor.targetFor("tst", fixedTime.toString());

        Assertions.assertEquals(CONTAINER, target.container());
        Assertions.assertEquals("imported/tst/tst_2025-06-15T10_30_45.123.zip", target.fileHandle());
    }

    @Test
    void anUnparseableTimestampStillProducesATarget() {
        // Nisaba is an archive: a bad timestamp header must not stop the dataset being written, so it falls
        // back to now rather than throwing.
        NisabaHeadersProcessor.UploadTarget target = processor.targetFor("tst", "not-a-timestamp");

        Assertions.assertTrue(target.fileHandle().startsWith("imported/tst/tst_"));
        Assertions.assertTrue(target.fileHandle().endsWith(".zip"));
    }
}

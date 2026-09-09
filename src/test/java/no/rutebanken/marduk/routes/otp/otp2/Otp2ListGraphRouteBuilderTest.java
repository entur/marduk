package no.rutebanken.marduk.routes.otp.otp2;

import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.domain.OtpGraphsInfo;
import no.rutebanken.marduk.graph.OtpGraphFilesBuilder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;

import static no.rutebanken.marduk.Constants.OTP2_CURRENT_GRAPH_OBJ;
import static no.rutebanken.marduk.routes.otp.otp2.Otp2ListGraphRouteBuilder.OTP2_NETEX_GRAPH_FILE_NAME_PATTERN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Otp2ListGraphRouteBuilderTest {

    @Test
    void timestampedGraphFileMatchesWithSerializationId() {
        Matcher m = OTP2_NETEX_GRAPH_FILE_NAME_PATTERN.matcher("netex-otp2/EN-0051/2026-09-08T03:00:00-Graph-otp2-EN-0051.obj");
        assertTrue(m.find());
        assertEquals("EN-0051", m.group(1));
    }

    @Test
    void stableCopyAndPointerFileDoNotMatch() {
        assertFalse(OTP2_NETEX_GRAPH_FILE_NAME_PATTERN.matcher("netex-otp2/EN-0051/" + OTP2_CURRENT_GRAPH_OBJ).find());
        assertFalse(OTP2_NETEX_GRAPH_FILE_NAME_PATTERN.matcher("netex-otp2/EN-0051/current-otp2").find());
    }

    @Test
    void latestGraphListedIsTheTimestampedFileNotTheStableCopy() {
        BlobStoreFiles.File timestamped = createFile("netex-otp2/EN-0051/2026-09-08T03:00:00-Graph-otp2-EN-0051.obj", Instant.EPOCH);
        // the stable copy is written after the timestamped file
        BlobStoreFiles.File stableCopy = createFile("netex-otp2/EN-0051/" + OTP2_CURRENT_GRAPH_OBJ, Instant.EPOCH.plusSeconds(1));
        List<OtpGraphsInfo.OtpGraphFile> graphFiles = new OtpGraphFilesBuilder()
                .withFileNameRegex(OTP2_NETEX_GRAPH_FILE_NAME_PATTERN)
                .withFiles(List.of(timestamped, stableCopy))
                .build();

        assertEquals(1, graphFiles.size());
        assertEquals("2026-09-08T03:00:00-Graph-otp2-EN-0051.obj", graphFiles.getFirst().name());
        assertEquals("EN-0051", graphFiles.getFirst().serializationId());
    }

    private static BlobStoreFiles.File createFile(String fileName, Instant creationTime) {
        BlobStoreFiles.File file = new BlobStoreFiles.File();
        file.setName(fileName);
        file.setCreated(creationTime);
        file.setFileSize(1L);
        return file;
    }
}

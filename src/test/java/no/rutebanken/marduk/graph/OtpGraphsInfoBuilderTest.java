package no.rutebanken.marduk.graph;

import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.domain.OtpGraphsInfo;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OtpGraphsInfoBuilderTest {

    @Test
    void test() {
        BlobStoreFiles.File graph1 = createFile("x/EN-001/1.obj", Instant.EPOCH, 10);
        BlobStoreFiles.File graph2 = createFile("x/EN-002/2.obj", Instant.EPOCH.plusSeconds(1), 11);
        BlobStoreFiles.File graph3 = createFile("x/EN-002/3.obj", Instant.EPOCH.plusSeconds(2), 12);
        BlobStoreFiles.File unknownFile = createFile("xxxx", Instant.EPOCH.plusSeconds(2), 12);
        Collection<BlobStoreFiles.File> files = List.of(graph1, graph2, graph3, unknownFile);
        List<OtpGraphsInfo.OtpGraphFile> otpGraphFiles = new OtpGraphFilesBuilder()
                .withFileNameRegex("x/" + "(.*)" + "/.*\\.obj")
                .withFiles(files)
                .build();

        assertNotNull(otpGraphFiles);
        assertEquals(2, otpGraphFiles.size());

    }

    @NotNull
    private static BlobStoreFiles.File createFile(String fileName, Instant creationTime, long fileSize) {
        BlobStoreFiles.File graph1 = new BlobStoreFiles.File();
        graph1.setName(fileName);
        graph1.setCreated(creationTime);
        graph1.setFileSize(fileSize);
        return graph1;
    }

}
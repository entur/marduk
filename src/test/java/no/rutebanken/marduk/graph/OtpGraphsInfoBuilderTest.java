package no.rutebanken.marduk.graph;

import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.domain.OtpGraphsInfo;
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

    @Test
    void customSerializationIdIsNotListedAsTheLatest() {
        // the tst bucket holds a graph built from a branch with the serialization id transfers1a
        BlobStoreFiles.File custom = createFile("netex-otp2/transfers1a/Graph-otp2-287.obj", Instant.EPOCH.plusSeconds(3), 10);
        BlobStoreFiles.File previous = createFile("netex-otp2/EN-0252/20261125051611611-Graph-otp2-EN-0252.obj", Instant.EPOCH.plusSeconds(1), 11);
        BlobStoreFiles.File latest = createFile("netex-otp2/EN-0256/20261126051611611-Graph-otp2-EN-0256.obj", Instant.EPOCH.plusSeconds(2), 12);
        List<OtpGraphsInfo.OtpGraphFile> otpGraphFiles = new OtpGraphFilesBuilder()
                .withFileNameRegex("netex-otp2/(.*)/.*\\.obj")
                .withFiles(List.of(custom, previous, latest))
                .build();

        assertEquals(List.of("EN-0256", "EN-0252"), otpGraphFiles.stream().map(OtpGraphsInfo.OtpGraphFile::serializationId).toList());
    }

    private static BlobStoreFiles.File createFile(String fileName, Instant creationTime, long fileSize) {
        BlobStoreFiles.File graph1 = new BlobStoreFiles.File();
        graph1.setName(fileName);
        graph1.setCreated(creationTime);
        graph1.setFileSize(fileSize);
        return graph1;
    }

}
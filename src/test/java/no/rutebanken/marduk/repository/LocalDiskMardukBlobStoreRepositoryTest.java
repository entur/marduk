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
 */

package no.rutebanken.marduk.repository;

import no.rutebanken.marduk.domain.BlobStoreFiles;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class LocalDiskMardukBlobStoreRepositoryTest {

    private static final String CONTAINER = "marduk";

    @TempDir
    private Path baseFolder;

    private LocalDiskMardukBlobStoreRepository repository;

    @BeforeEach
    void setUp() throws IOException {
        repository = new LocalDiskMardukBlobStoreRepository(baseFolder.toString());
        repository.setContainerName(CONTAINER);
        createBlob("outbound/netex/rb_vyg-aggregated-netex.zip");
        createBlob("outbound/netex/rb_rut-aggregated-netex.zip");
        createBlob("outbound/netex-dsj-new/rb_vyg-aggregated-netex.zip");
    }

    @Test
    void testListBlobsWithFolderPrefix() {
        Assertions.assertEquals(
                List.of("outbound/netex/rb_rut-aggregated-netex.zip", "outbound/netex/rb_vyg-aggregated-netex.zip"),
                names(repository.listBlobs("outbound/netex/")));
    }

    @Test
    void testListBlobsWithCompleteBlobNameAsPrefix() {
        Assertions.assertEquals(
                List.of("outbound/netex/rb_vyg-aggregated-netex.zip"),
                names(repository.listBlobs("outbound/netex/rb_vyg-aggregated-netex.zip")));
    }

    @Test
    void testListBlobsWithPartialBlobNameAsPrefix() {
        Assertions.assertEquals(
                List.of("outbound/netex-dsj-new/rb_vyg-aggregated-netex.zip"),
                names(repository.listBlobs("outbound/netex-dsj-new/rb_vyg-")));
    }

    @Test
    void testListBlobsWithUnknownPrefix() {
        Assertions.assertTrue(repository.listBlobs("outbound/netex-dsj-legacy/rb_vyg-aggregated-netex.zip").getFiles().isEmpty());
        Assertions.assertTrue(repository.listBlobs("outbound/netex/rb_flt-").getFiles().isEmpty());
    }

    @Test
    void testListBlobsWithSeveralPrefixes() {
        Assertions.assertEquals(
                List.of("outbound/netex-dsj-new/rb_vyg-aggregated-netex.zip", "outbound/netex/rb_vyg-aggregated-netex.zip"),
                names(repository.listBlobs(List.of("outbound/netex/rb_vyg-aggregated-netex.zip", "outbound/netex-dsj-new/rb_vyg-aggregated-netex.zip"))));
    }

    private void createBlob(String name) throws IOException {
        Path path = baseFolder.resolve(CONTAINER).resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, name);
    }

    private static List<String> names(BlobStoreFiles blobStoreFiles) {
        return blobStoreFiles.getFiles().stream().map(BlobStoreFiles.File::getName).sorted().toList();
    }
}

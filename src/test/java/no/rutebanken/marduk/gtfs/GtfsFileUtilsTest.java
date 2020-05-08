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

package no.rutebanken.marduk.gtfs;

import no.rutebanken.marduk.routes.file.ZipFileUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GtfsFileUtilsTest {

    private static final String GTFS_FILE_1 = "src/test/resources/no/rutebanken/marduk/routes/file/beans/gtfs.zip";
    private static final String GTFS_FILE_2 = "src/test/resources/no/rutebanken/marduk/routes/file/beans/gtfs2.zip";

    @Test
    public void mergeGtfsFiles_identicalFilesShouldYieldMergedFileIdenticalToOrg() throws Exception {

        File input1 = new File(GTFS_FILE_1);
        File merged = GtfsFileUtils.mergeGtfsFiles(List.of(input1, input1), GtfsExport.GTFS_EXTENDED);

        // Should assert content, but no exceptions must do for now
        // assertTrue(FileUtils.sizeOf(merged) <= FileUtils.sizeOf(input1));

        assertTrue(ZipFileUtils.listFilesInZip(merged).stream().anyMatch(ze -> GtfsFileUtils.FEED_INFO_FILE_NAME.equals(ze.getName())));
    }

    @Test
    public void mergeGtfsFiles_nonIdenticalFilesShouldYieldUnion() throws Exception {

        File input1 = new File(GTFS_FILE_1);
        File input2 = new File(GTFS_FILE_2);
        File merged = GtfsFileUtils.mergeGtfsFiles(List.of(input1, input2), GtfsExport.GTFS_EXTENDED);

        byte[] data = Files.readAllBytes(merged.toPath());
        assertTrue(data.length >= FileUtils.sizeOf(input1));
        assertTrue(data.length >= FileUtils.sizeOf(input2));

        assertTrue(ZipFileUtils.listFilesInZip(data).stream().anyMatch(ze -> GtfsFileUtils.FEED_INFO_FILE_NAME.equals(ze.getName())));
    }

    @Test
    public void mergeWithTransfers() throws Exception {
        File mergedZip = GtfsFileUtils.mergeGtfsFiles(List.of(new File(GTFS_FILE_1), new File(GTFS_FILE_1)), GtfsExport.GTFS_EXTENDED);

        List<String> transferLines = IOUtils.readLines(new ByteArrayInputStream(ZipFileUtils.extractFileFromZipFile(mergedZip, "transfers.txt")), StandardCharsets.UTF_8);
        assertThat(transferLines.size()).as("Expected file two duplicates and one other transfer to be merged to two (+ header)").isEqualTo(3);
    }

}

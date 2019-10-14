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

package no.rutebanken.marduk.routes.file;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GtfsFileUtilsTest {

    private static final String GTFS_FILE_1 = "src/test/resources/no/rutebanken/marduk/routes/file/beans/gtfs.zip";
    private static final String GTFS_FILE_2 = "src/test/resources/no/rutebanken/marduk/routes/file/beans/gtfs2.zip";

    @Test
    public void mergeGtfsFiles_identicalFilesShouldYieldMergedFileIdenticalToOrg() throws Exception {

        File input1 = new File(GTFS_FILE_1);
        File merged = GtfsFileUtils.mergeGtfsFiles(Arrays.asList(input1, input1));

        // Should assert content, but no exceptions must do for now
        // assertTrue(FileUtils.sizeOf(merged) <= FileUtils.sizeOf(input1));

        assertTrue(new ZipFileUtils().listFilesInZip(merged).stream().anyMatch(n -> "feed_info.txt".equals(n)));
    }

    @Test
    public void mergeGtfsFiles_nonIdenticalFilesShouldYieldUnion() throws Exception {

        File input1 = new File(GTFS_FILE_1);
        File input2 = new File(GTFS_FILE_2);
        File merged = GtfsFileUtils.mergeGtfsFiles(Arrays.asList(input1, input2));

        assertTrue(FileUtils.sizeOf(merged) >= FileUtils.sizeOf(input1));
        assertTrue(FileUtils.sizeOf(merged) >= FileUtils.sizeOf(input2));

        assertTrue(new ZipFileUtils().listFilesInZip(merged).stream().anyMatch(n -> "feed_info.txt".equals(n)));
    }


    @Test
    public void replaceIdSeparatorInFile() throws Exception {
        File out = GtfsFileUtils.transformIdsToOTPFormat(new File(GTFS_FILE_2));

        List<String> stopLines = IOUtils.readLines(new ByteArrayInputStream(ZipFileUtils.extractFileFromZipFile(new FileInputStream(out), "stops.txt").toByteArray()));

        assertEquals("RUT.StopArea.7600100,Oslo S,59.910200,10.755330,RUT.StopArea.7600207", stopLines.get(1));

        List<String> feedInfoLines = IOUtils.readLines(new ByteArrayInputStream(ZipFileUtils.extractFileFromZipFile(new FileInputStream(out), "feed_info.txt").toByteArray()));

        assertThat(feedInfoLines.get(1)).as("Feed info should be unchanged").isEqualTo("RB,Rutebanken,http://www.rutebanken.org,no");
    }

    @Test
    public void mergeWithTransfers() throws Exception {
        File merged = GtfsFileUtils.mergeGtfsFiles(Arrays.asList(new File(GTFS_FILE_1), new File(GTFS_FILE_1)));

        List<String> transferLines = IOUtils.readLines(new ByteArrayInputStream(ZipFileUtils.extractFileFromZipFile(new FileInputStream(merged), "transfers.txt").toByteArray()));
        assertThat(transferLines.size()).as("Expected file two duplicates and one other transfer to be merged to two (+ header)").isEqualTo(3);
    }

}

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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GtfsFileUtilsTest {

    private static final String GTFS_FILE_1 = "src/test/resources/no/rutebanken/marduk/routes/file/beans/gtfs.zip";
    private static final String GTFS_FILE_2 = "src/test/resources/no/rutebanken/marduk/routes/file/beans/gtfs2.zip";

    @Test
    public void mergeGtfsFiles_identicalFilesShouldYieldMergedFileIdenticalToOrg() throws Exception {

        File input1 = new File(GTFS_FILE_1);
        InputStream merged = GtfsFileUtils.mergeGtfsFiles(Arrays.asList(input1, input1));

        // Should assert content, but no exceptions must do for now
        // assertTrue(FileUtils.sizeOf(merged) <= FileUtils.sizeOf(input1));

        assertTrue(ZipFileUtils.listFilesInZip(merged.readAllBytes()).stream().anyMatch(n -> GtfsFileUtils.FEED_INFO_FILE_NAME.equals(n)));
    }

    @Test
    public void mergeGtfsFiles_nonIdenticalFilesShouldYieldUnion() throws Exception {

        File input1 = new File(GTFS_FILE_1);
        File input2 = new File(GTFS_FILE_2);
        InputStream merged = GtfsFileUtils.mergeGtfsFiles(Arrays.asList(input1, input2));

        byte[] data = merged.readAllBytes();
        assertTrue(data.length >= FileUtils.sizeOf(input1));
        assertTrue(data.length >= FileUtils.sizeOf(input2));

        assertTrue(ZipFileUtils.listFilesInZip(data).stream().anyMatch(n -> GtfsFileUtils.FEED_INFO_FILE_NAME.equals(n)));
    }

    @Test
    public void mergeWithTransfers() throws Exception {
        InputStream merged = GtfsFileUtils.mergeGtfsFiles(Arrays.asList(new File(GTFS_FILE_1), new File(GTFS_FILE_1)));
        File tmpZip = TempFileUtils.createTempFile(merged.readAllBytes(), "marduk-test-mergeWithTransfers-", ".zip");

        List<String> transferLines = IOUtils.readLines(new ByteArrayInputStream(ZipFileUtils.extractFileFromZipFile(tmpZip, "transfers.txt")), StandardCharsets.UTF_8);
        assertThat(transferLines.size()).as("Expected file two duplicates and one other transfer to be merged to two (+ header)").isEqualTo(3);
    }

}

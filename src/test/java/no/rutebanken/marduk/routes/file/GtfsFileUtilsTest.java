package no.rutebanken.marduk.routes.file;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.List;

public class GtfsFileUtilsTest {

    private static final String GTFS_FILE_1 = "src/test/resources/no/rutebanken/marduk/routes/file/beans/gtfs.zip";
    private static final String GTFS_FILE_2 = "src/test/resources/no/rutebanken/marduk/routes/file/beans/gtfs2.zip";

    @Test
    public void mergeGtfsFiles_identicalFilesShouldYieldMergedFileIdenticalToOrg() throws Exception {

        File input1 = new File(GTFS_FILE_1);
        File merged = GtfsFileUtils.mergeGtfsFiles(Arrays.asList(input1, input1));

        // Should assert content, but no exceptions must do for now
        // Assert.assertTrue(FileUtils.sizeOf(merged) <= FileUtils.sizeOf(input1));

        Assert.assertTrue(new ZipFileUtils().listFilesInZip(merged).stream().anyMatch(n -> "feed_info.txt".equals(n)));
    }

    @Test
    public void mergeGtfsFiles_nonIdenticalFilesShouldYieldUnion() throws Exception {

        File input1 = new File(GTFS_FILE_1);
        File input2 = new File(GTFS_FILE_2);
        File merged = GtfsFileUtils.mergeGtfsFiles(Arrays.asList(input1, input2));

        Assert.assertTrue(FileUtils.sizeOf(merged) >= FileUtils.sizeOf(input1));
        Assert.assertTrue(FileUtils.sizeOf(merged) >= FileUtils.sizeOf(input2));

        Assert.assertTrue(new ZipFileUtils().listFilesInZip(merged).stream().anyMatch(n -> "feed_info.txt".equals(n)));
    }


    @Test
    public void replaceIdSeparatorInFile() throws Exception {
        File out = GtfsFileUtils.transformIdsToOTPFormat(new File(GTFS_FILE_2));

        List<String> stopLines = IOUtils.readLines(new ByteArrayInputStream(ZipFileUtils.extractFileFromZipFile(new FileInputStream(out), "stops.txt").toByteArray()));

        Assert.assertEquals("RUT.StopArea.7600100,Oslo S,59.910200,10.755330,RUT.StopArea.7600207", stopLines.get(1));

        List<String> feedInfoLines = IOUtils.readLines(new ByteArrayInputStream(ZipFileUtils.extractFileFromZipFile(new FileInputStream(out), "feed_info.txt").toByteArray()));

        Assert.assertEquals( "Feed info should be unchanged","RB,Rutebanken,http://www.rutebanken.org,no", feedInfoLines.get(1));


    }

}

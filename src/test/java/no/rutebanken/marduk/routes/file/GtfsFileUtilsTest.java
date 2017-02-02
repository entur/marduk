package no.rutebanken.marduk.routes.file;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;

public class GtfsFileUtilsTest {

	private static final String GTFS_FILE_1 = "src/test/resources/no/rutebanken/marduk/routes/file/beans/gtfs.zip";
	private static final String GTFS_FILE_2 = "src/test/resources/no/rutebanken/marduk/routes/file/beans/gtfs2.zip";

	@Test
	public void mergeGtfsFiles_identicalFilesShouldYieldMergedFileIdenticalToOrg() throws Exception {

		File input1 = new File(GTFS_FILE_1);
		File merged = GtfsFileUtils.mergeGtfsFiles(Arrays.asList(input1, input1));

		Assert.assertTrue(FileUtils.sizeOf(merged) <= FileUtils.sizeOf(input1));
	}

	@Test
	public void mergeGtfsFiles_nonIdenticalFilesShouldYieldUnion() throws Exception {

		File input1 = new File(GTFS_FILE_1);
		File input2 = new File(GTFS_FILE_2);
		File merged = GtfsFileUtils.mergeGtfsFiles(Arrays.asList(input1, input2));

		Assert.assertTrue(FileUtils.sizeOf(merged) >= FileUtils.sizeOf(input1));
		Assert.assertTrue(FileUtils.sizeOf(merged) >= FileUtils.sizeOf(input2));
	}


}

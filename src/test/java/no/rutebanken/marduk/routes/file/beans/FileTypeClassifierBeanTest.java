package no.rutebanken.marduk.routes.file.beans;

import no.rutebanken.marduk.exceptions.FileValidationException;
import no.rutebanken.marduk.routes.file.FileType;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import static no.rutebanken.marduk.routes.file.FileType.GTFS;
import static no.rutebanken.marduk.routes.file.FileType.NETEXPROFILE;
import static org.junit.Assert.assertEquals;

public class FileTypeClassifierBeanTest {

    private FileTypeClassifierBean bean;

    @Before
    public void before() {
        bean = new FileTypeClassifierBean();
    }

    @Test
    public void classifyGtfsFile() throws Exception {
        assertFileType("gtfs.zip", GTFS);
    }

    @Test
    public void classifyGtfsFileContainingFolder() throws Exception {
        // The file is known to be invalid - repack zip
        File rePackedZipFile = ZipFileUtils.rePackZipFile(IOUtils.toByteArray(this.getClass().getResourceAsStream("gtfs-folder.zip")));
        assertFileType(rePackedZipFile, GTFS);
    }

    @Test
    public void classifyNetexFile() throws Exception {
        assertFileType("netex.zip", NETEXPROFILE);
    }

    @Test(expected = RuntimeException.class)
    public void classifyNetexFileFromRuter() throws Exception {
        assertFileType("AOR.zip", NETEXPROFILE);
    }

    @Test
    public void classifyNetexWithNeptuneFileNameInside() throws Exception {
        assertFileType("netex_with_neptune_file_name_inside.zip", NETEXPROFILE);
    }

    @Test
    public void classifyNetexWithTwoFiles() throws Exception {
        assertFileType("netex_with_two_files.zip", NETEXPROFILE);
    }

    @Test (expected = FileValidationException.class)
    public void classifyNetexWithTwoFilesOneInvalid() throws Exception {
        assertFileType("netex_with_two_files_one_invalid.zip", NETEXPROFILE);
    }

    private void assertFileType(String fileName, FileType expectedFileType) throws IOException {
        byte[] data = IOUtils.toByteArray(this.getClass().getResourceAsStream(fileName));
        assertFileType(fileName, data, expectedFileType);
    }

    private void assertFileType(File file, FileType expectedFileType) throws IOException {
        byte[] data = IOUtils.toByteArray(new FileInputStream(file));
        assertFileType(file.getName(), data, expectedFileType);
    }

    private void assertFileType(String fileName, byte[] data, FileType expectedFileType) {
        FileType resultType = bean.classifyFile(fileName, data);
        assertEquals(expectedFileType, resultType);
    }

}

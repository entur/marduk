package no.rutebanken.marduk.routes.file.beans;

import no.rutebanken.marduk.exceptions.FileValidationException;
import no.rutebanken.marduk.routes.file.FileType;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

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
    public void classifyNetexFile() throws Exception {
        assertFileType("netex.zip", NETEXPROFILE);
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
        FileType resultType = bean.classifyFile(fileName, data);
        assertEquals(expectedFileType, resultType);
    }

}

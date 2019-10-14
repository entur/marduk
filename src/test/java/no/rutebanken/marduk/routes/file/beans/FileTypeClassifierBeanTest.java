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

package no.rutebanken.marduk.routes.file.beans;

import no.rutebanken.marduk.exceptions.FileValidationException;
import no.rutebanken.marduk.routes.file.FileType;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import org.apache.commons.io.IOUtils;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import static no.rutebanken.marduk.routes.file.FileType.*;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FileTypeClassifierBeanTest {

    private FileTypeClassifierBean bean;

    @BeforeEach
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

    @Test
    public void classifyNetexFileFromRuter() throws Exception {
    	assertThrows(RuntimeException.class, () ->
    		assertFileType("AOR.zip", NETEXPROFILE)
   		);
    }

    @Test
    public void classifyNetexWithNeptuneFileNameInside() throws Exception {
        assertFileType("netex_with_neptune_file_name_inside.zip", NETEXPROFILE);
    }

    @Test
    public void classifyNetexWithTwoFiles() throws Exception {
        assertFileType("netex_with_two_files.zip", NETEXPROFILE);
    }

    @Test
    public void classifyNetexWithTwoFilesOneInvalid() throws Exception {
    	assertThrows(FileValidationException.class, () ->
        	assertFileType("netex_with_two_files_one_invalid.zip", NETEXPROFILE)
        );
    }

    @Test
    public void classifyFileNameWithNonISO_8859_1CharacterAsInvalid() throws Exception {
        // The å in ekspressbåt below is encoded as 97 ('a') + 778 (ring above)
        byte[] data = IOUtils.toByteArray(this.getClass().getResourceAsStream("netex.zip"));
        assertFileType("sof-20170904121616-2907_20170904_Buss_og_ekspressbåt_til_rutesøk_19.06.2017-28.02.2018 (1).zip", data, INVALID_FILE_NAME);
    }

    @Test
    public void classifyFileNameWithOnlyISO_8859_1CharacterAsValid() throws Exception {
        // The å in ekspressbåt below is encoded as a regular 229 ('å')
        byte[] data = IOUtils.toByteArray(this.getClass().getResourceAsStream("netex.zip"));
        assertFileType("sof-20170904121616-2907_20170904_Buss_og_ekspressbåt_til_rutesøk_19.06.2017-28.02.2018 (1).zip", data, NETEXPROFILE);
    }

    @Test
    public void nonXMLFilePatternShouldMatchOtherFileTypes() {
        assertTrue("test.log".matches(FileTypeClassifierBean.NON_XML_FILE_XML));
        assertTrue("test.xml.log".matches(FileTypeClassifierBean.NON_XML_FILE_XML));
        assertTrue("test.xml2".matches(FileTypeClassifierBean.NON_XML_FILE_XML));
        assertTrue("test.txml".matches(FileTypeClassifierBean.NON_XML_FILE_XML));
    }

    @Test
    public void nonXMLFilePatternShouldNotMatchXMLFiles() {
        assertFalse("test.xml".matches(FileTypeClassifierBean.NON_XML_FILE_XML));
        assertFalse("test.XML".matches(FileTypeClassifierBean.NON_XML_FILE_XML));
        assertFalse("test.test.xml".matches(FileTypeClassifierBean.NON_XML_FILE_XML));
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

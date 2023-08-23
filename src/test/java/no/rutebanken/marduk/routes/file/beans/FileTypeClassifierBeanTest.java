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

import no.rutebanken.marduk.routes.file.FileType;
import org.apache.commons.io.IOUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static no.rutebanken.marduk.routes.file.FileType.*;

import static org.junit.jupiter.api.Assertions.*;

class FileTypeClassifierBeanTest {

    private FileTypeClassifierBean bean;

    @BeforeEach
    void before() {
        bean = new FileTypeClassifierBean();
    }

    @Test
    void classifyGtfsFile() throws Exception {
        assertFileType("gtfs.zip", GTFS);
    }

    @Test
    void classifyNetexFile() throws Exception {
        assertFileType("netex.zip", NETEXPROFILE);
    }

    @Test
    void classifyNetexFileFromRuter() throws IOException {
        assertFileType("AOR.zip", INVALID_ZIP_FILE_ENTRY_XML_CONTENT);
    }

    @Test
    void classifyNetexWithNeptuneFileNameInside() throws Exception {
        assertFileType("netex_with_neptune_file_name_inside.zip", NETEXPROFILE);
    }

    @Test
    void classifyNetexWithTwoFiles() throws Exception {
        assertFileType("netex_with_two_files.zip", NETEXPROFILE);
    }

    @Test
    void classifyNetexWithTwoFilesOneInvalid() throws IOException {
    	assertFileType("netex_with_two_files_one_invalid.zip", UNKNOWN_FILE_TYPE);

    }

    @Test
    void classifyNotAZipFile() throws IOException {
        assertFileType("not_a_zip_file.zip", NOT_A_ZIP_FILE);
    }

    @Test
    void classifyZipFilContainsSubdirectories() throws IOException {
        assertFileType("gtfs-folder.zip", ZIP_CONTAINS_SUBDIRECTORIES);
        assertFileType("gtfs-folder-without-folder-entry.zip", ZIP_CONTAINS_SUBDIRECTORIES);
    }

    @Test
    void classifyEmptyZipFile() throws IOException {
        assertFileType("empty_zip.zip", NOT_A_ZIP_FILE);
    }

    @Test
    void classifyInvalidEncodingInZipEntryName() throws IOException {
        assertFileType("zip_file_with_invalid_encoding_in_entry_name.zip", INVALID_ZIP_FILE_ENTRY_NAME_ENCODING);
    }

    @Test
    void classifyUnknownFileType() throws IOException {
        assertFileType("unknown_file_type.zip", UNKNOWN_FILE_TYPE);
    }

    @Test
    void classifyFileNameWithNonISO_8859_1CharacterAsInvalid() throws Exception {
        // The å in ekspressbåt below is encoded as 97 ('a') + 778 (ring above)
        byte[] data = IOUtils.toByteArray(this.getClass().getResourceAsStream("netex.zip"));
        assertFileType("sof-20170904121616-2907_20170904_Buss_og_ekspressbåt_til_rutesøk_19.06.2017-28.02.2018 (1).zip", data, INVALID_FILE_NAME);
    }

    @Test
    void classifyFileNameWithOnlyISO_8859_1CharacterAsValid() throws Exception {
        // The å in ekspressbåt below is encoded as a regular 229 ('å')
        byte[] data = IOUtils.toByteArray(this.getClass().getResourceAsStream("netex.zip"));
        assertFileType("sof-20170904121616-2907_20170904_Buss_og_ekspressbåt_til_rutesøk_19.06.2017-28.02.2018 (1).zip", data, NETEXPROFILE);
    }

    @Test
    void xMLFilePatternShouldMatchXMLFiles() {
        assertTrue(FileTypeClassifierBean.XML_FILES_REGEX.matcher("test.xml").matches());
        assertTrue(FileTypeClassifierBean.XML_FILES_REGEX.matcher("test.test.xml").matches());
    }

    @Test
    void xMLFilePatternShouldNotMatchOtherFileTypes() {
        assertFalse(FileTypeClassifierBean.XML_FILES_REGEX.matcher("test.log").matches());
        assertFalse(FileTypeClassifierBean.XML_FILES_REGEX.matcher("test.xml.log").matches());
        assertFalse(FileTypeClassifierBean.XML_FILES_REGEX.matcher("test.xml2").matches());
        assertFalse(FileTypeClassifierBean.XML_FILES_REGEX.matcher("test.txml").matches());
    }

    @Test
    void xMLFilePatternShouldMatchOnlyLowerCaseExtension() {
        assertFalse(FileTypeClassifierBean.XML_FILES_REGEX.matcher("test.XML").matches());
        assertFalse(FileTypeClassifierBean.XML_FILES_REGEX.matcher("test.Xml").matches());
    }

    private void assertFileType(String fileName, FileType expectedFileType) throws IOException {
        byte[] data = IOUtils.toByteArray(this.getClass().getResourceAsStream(fileName));
        assertFileType(fileName, data, expectedFileType);
    }


    private void assertFileType(String fileName, byte[] data, FileType expectedFileType) {
        FileType resultType = bean.classifyFile(fileName, data);
        assertEquals(expectedFileType, resultType);
    }

}

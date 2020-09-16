package no.rutebanken.marduk.routes.file;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MardukFileUtilsTest {

    @Test
    void testIsValidFileName() {
        assertTrue(MardukFileUtils.isValidFileName("test.xml"), "A Filename without special character is valid");
        assertTrue(MardukFileUtils.isValidFileName("ÅØÆåøæ.xml"), "A Filename with ISO-8859-1 characters is valid");
        assertFalse(MardukFileUtils.isValidFileName("谢谢.xml"), "A Filename with non ISO-8859-1 characters is invalid");
        assertFalse(MardukFileUtils.isValidFileName("aa\naa.xml"),"A Filename with new line characters is invalid");
        assertFalse(MardukFileUtils.isValidFileName("aa\raa.xml"),"A Filename with carriage return characters is invalid");
        assertFalse(MardukFileUtils.isValidFileName("aa\taa.xml"), "A Filename with tab characters is invalid");

    }

    @Test
    void testSanitizeFileName() {
        assertEquals("test.xml", MardukFileUtils.sanitizeFileName("test.xml"), "A Filename without special character is unchanged");
        assertEquals("ÅØÆåøæ.xml", MardukFileUtils.sanitizeFileName("ÅØÆåøæ.xml"), "A Filename with ISO-8859-1 characters is unchanged");
        assertEquals(".xml", MardukFileUtils.sanitizeFileName("谢谢.xml"), "Non ISO-8859-1 characters are stripped");
        assertEquals("aaaa.xml", MardukFileUtils.sanitizeFileName("aa\naa.xml"),"New line characters are stripped");
        assertEquals("aaaa.xml", MardukFileUtils.sanitizeFileName("aa\raa.xml"),"Carriage return characters are stripped");
        assertEquals("aaaa.xml", MardukFileUtils.sanitizeFileName("aa\taa.xml"), "Tab characters are stripped");
    }
}

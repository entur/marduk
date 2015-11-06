package no.rutebanken.marduk.beans;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class Md5BeanTest {

    @Test
    public void testGenerateMd5() throws Exception {
        assertEquals("Wrong md5 code", "834ebcac61b4f768a1c1d1788831695e", new Md5Bean().generateMd5(Files.readAllBytes(Paths.get("target/test-classes/file_to_test_md5_hashing.txt"))));
    }

    @Test (expected = NullPointerException.class)
    public void testGenerateMd5WithNull() throws Exception {
        assertEquals("Wrong md5 code", "", new Md5Bean().generateMd5(null));
    }

    @Test
    public void testGenerateMd5WithEmptyData() throws Exception {
        assertEquals("Wrong md5 code", "d41d8cd98f00b204e9800998ecf8427e", new Md5Bean().generateMd5(new byte[]{}));
    }
}
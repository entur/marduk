package no.rutebanken.marduk.domain;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BlobStoreFilesTest {

    @Test
    void testGetFileNameOnly() {
        BlobStoreFiles.File file = new BlobStoreFiles.File();
        file.setName("a/b/c/d");
        Assertions.assertEquals("d", file.getFileNameOnly());
    }

    @Test
    void testGetFileNameOnlyWithDirectory() {
        BlobStoreFiles.File file = new BlobStoreFiles.File();
        file.setName("a/b/c/");
        Assertions.assertNull(file.getFileNameOnly());
    }
}

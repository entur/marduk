package no.rutebanken.marduk.routes.file.beans;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static no.rutebanken.marduk.routes.file.beans.FileClassifierPredicates.*;

public class FileClassifierPredicatesTest {

    @Test
    public void firstElementQNameMatchesNetex() throws Exception {
        assertPredicateTrueInZipFile("netex_with_two_files.zip",
                FileClassifierPredicates.firstElementQNameMatchesNetex());
    }

    @Test
    public void firstElementQNameMatchesNetexNot() throws Exception {
        assertPredicateFalseInZipFile("netex_with_two_files_one_invalid.zip",
                FileClassifierPredicates.firstElementQNameMatchesNetex());
    }

    @Test
    public void firstElementQNameMatches() throws Exception {
        assertPredicateTrueInZipFile("netex_with_two_files.zip",
                FileClassifierPredicates.firstElementQNameMatches(
                        NETEX_PUBLICATION_DELIVERY_QNAME));
    }

    @Test
    public void firstElementQNameMatchesNot() throws Exception {
        assertPredicateFalseInZipFile("netex_with_two_files_one_invalid.zip",
                FileClassifierPredicates.firstElementQNameMatches(
                        NETEX_PUBLICATION_DELIVERY_QNAME));
    }

    private void assertPredicateTrueInZipFile(String fileName, Predicate<InputStream> predicate) throws IOException {
        ZipInputStream zipInputStream = new ZipInputStream(this.getClass().getResourceAsStream(fileName));
        while (zipInputStream.getNextEntry() != null) {
            if (!predicate.test(zipInputStream)) {
                throw new AssertionError();
            }
        }
    }

    private void assertPredicateFalseInZipFile(String fileName, Predicate<InputStream> predicate) throws IOException {
        ZipInputStream zipInputStream = new ZipInputStream(this.getClass().getResourceAsStream(fileName));
        while (zipInputStream.getNextEntry() != null) {
            if (!predicate.test(zipInputStream)) {
                return;
            }
        }
        throw new AssertionError();
    }

}
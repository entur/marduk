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
    public void firstElementQNameMatchesComment() throws Exception {
        assertPredicateTrueInZipFile("netex_with_initial_comment.zip",
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
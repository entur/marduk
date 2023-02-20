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

package no.rutebanken.marduk.repository;


import no.rutebanken.marduk.MardukSpringBootBaseTest;
import no.rutebanken.marduk.TestApp;
import no.rutebanken.marduk.domain.FileNameAndDigest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = TestApp.class)
class FileNameAndDigestIdempotentRepositoryTest extends MardukSpringBootBaseTest {

    @Autowired
    private FileNameAndDigestIdempotentRepository idempotentRepository;

    @BeforeEach
    protected void clearIdempotentRepo() {
        idempotentRepository.clear();
    }

    @Test
    void testNonUniqueFileNameRejected() {
        FileNameAndDigest fileNameAndDigest = new FileNameAndDigest("fileName", "digestOne");
        assertTrue(idempotentRepository.add(fileNameAndDigest.toString()));

        FileNameAndDigest nonUniqueFileName = new FileNameAndDigest(fileNameAndDigest.getFileName(), "digestOther");
        assertFalse(idempotentRepository.add(nonUniqueFileName.toString()));
    }

    @Test
    void testNonUniqueDigestRejected() {
        FileNameAndDigest fileNameAndDigest = new FileNameAndDigest("fileName", "digestOne");
        assertTrue(idempotentRepository.add(fileNameAndDigest.toString()));

        FileNameAndDigest nonUniqueDigest = new FileNameAndDigest("fileNameOther", fileNameAndDigest.getDigest());
        assertFalse(idempotentRepository.add(nonUniqueDigest.toString()));
    }

    @Test
    void testNonUniqueFileNameAndDigestRejected() {
        FileNameAndDigest fileNameAndDigest = new FileNameAndDigest("fileName", "digestOne");
        assertTrue(idempotentRepository.add(fileNameAndDigest.toString()));

        assertFalse(idempotentRepository.add(fileNameAndDigest.toString()));
    }

    @Test
    void testUniqueFileNameAndDigestAccepted() {
        FileNameAndDigest fileNameAndDigest = new FileNameAndDigest("fileName", "digestOne");
        assertTrue(idempotentRepository.add(fileNameAndDigest.toString()));

        FileNameAndDigest nonUniqueFileName = new FileNameAndDigest("fileNameOther", "digestOther");
        assertTrue(idempotentRepository.add(nonUniqueFileName.toString()));
    }


    @Test
    void testRemoveEntryIfAddedRecently() {
        FileNameAndDigest fileNameAndDigest = new FileNameAndDigest("fileName", "digestOne");
        assertTrue(idempotentRepository.add(fileNameAndDigest.toString()));

        assertTrue(idempotentRepository.remove(fileNameAndDigest.toString()));
        assertFalse(idempotentRepository.contains(fileNameAndDigest.toString()));
    }

    @Test
    void testRemoveEntryIfAddedYesterday() {
        FileNameAndDigest fileNameAndDigest = new FileNameAndDigest("fileName", "digestOne");

        idempotentRepository.insert(fileNameAndDigest.toString(), Instant.now().minus(1, ChronoUnit.DAYS));

        assertTrue(idempotentRepository.contains(fileNameAndDigest.toString()));

        assertFalse(idempotentRepository.remove(fileNameAndDigest.toString()));
        assertTrue(idempotentRepository.contains(fileNameAndDigest.toString()));
    }

}

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


import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.TestApp;
import no.rutebanken.marduk.domain.FileNameAndDigest;
import org.apache.commons.lang3.time.DateUtils;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Timestamp;
import java.util.Date;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestApp.class)
class FileNameAndDigestIdempotentRepositoryTest extends MardukRouteBuilderIntegrationTestBase {

    @Autowired
    private FileNameAndDigestIdempotentRepository idempotentRepository;

    @Test
    void testNonUniqueFileNameRejected() {
        idempotentRepository.clear();
        FileNameAndDigest fileNameAndDigest = new FileNameAndDigest("fileName", "digestOne");
        assertTrue(idempotentRepository.add(fileNameAndDigest.toString()));

        FileNameAndDigest nonUniqueFileName = new FileNameAndDigest(fileNameAndDigest.getFileName(), "digestOther");
        assertFalse(idempotentRepository.add(nonUniqueFileName.toString()));
    }

    @Test
    void testNonUniqueDigestRejected() {
        idempotentRepository.clear();
        FileNameAndDigest fileNameAndDigest = new FileNameAndDigest("fileName", "digestOne");
        assertTrue(idempotentRepository.add(fileNameAndDigest.toString()));

        FileNameAndDigest nonUniqueDigest = new FileNameAndDigest("fileNameOther", fileNameAndDigest.getDigest());
        assertFalse(idempotentRepository.add(nonUniqueDigest.toString()));
    }

    @Test
    void testNonUniqueFileNameAndDigestRejected() {
        idempotentRepository.clear();
        FileNameAndDigest fileNameAndDigest = new FileNameAndDigest("fileName", "digestOne");
        assertTrue(idempotentRepository.add(fileNameAndDigest.toString()));

        assertFalse(idempotentRepository.add(fileNameAndDigest.toString()));
    }

    @Test
    void testUniqueFileNameAndDigestAccepted() {
        idempotentRepository.clear();
        FileNameAndDigest fileNameAndDigest = new FileNameAndDigest("fileName", "digestOne");
        assertTrue(idempotentRepository.add(fileNameAndDigest.toString()));

        FileNameAndDigest nonUniqueFileName = new FileNameAndDigest("fileNameOther", "digestOther");
        assertTrue(idempotentRepository.add(nonUniqueFileName.toString()));
    }


    @Test
    void testRemoveEntryIfAddedRecently() {
        idempotentRepository.clear();
        FileNameAndDigest fileNameAndDigest = new FileNameAndDigest("fileName", "digestOne");
        assertTrue(idempotentRepository.add(fileNameAndDigest.toString()));

        assertTrue(idempotentRepository.remove(fileNameAndDigest.toString()));
        assertFalse(idempotentRepository.contains(fileNameAndDigest.toString()));
    }

    @Test
    void testRemoveEntryIfAddedYesterday() {
        idempotentRepository.clear();
        FileNameAndDigest fileNameAndDigest = new FileNameAndDigest("fileName", "digestOne");

        idempotentRepository.insert(fileNameAndDigest.toString(), new Timestamp(DateUtils.addDays(new Date(), -1).getTime()));

        assertTrue(idempotentRepository.contains(fileNameAndDigest.toString()));

        assertFalse(idempotentRepository.remove(fileNameAndDigest.toString()));
        assertTrue(idempotentRepository.contains(fileNameAndDigest.toString()));
    }

}

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


import no.rutebanken.marduk.config.IdempotentRepositoryConfig;
import no.rutebanken.marduk.domain.FileNameAndDigest;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.Date;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = IdempotentRepositoryConfig.class, properties = "spring.main.sources=no.rutebanken.marduk.test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class FileNameAndDigestIdempotentRepositoryTest {

    @Autowired
    private FileNameAndDigestIdempotentRepository idempotentRepository;

    @Autowired
    private DataSource datasource;


    @Test
    public void testNonUniqueFileNameRejected() {
        idempotentRepository.clear();
        FileNameAndDigest fileNameAndDigest = new FileNameAndDigest("fileName", "digestOne");
        Assert.assertTrue(idempotentRepository.add(fileNameAndDigest.toString()));

        FileNameAndDigest nonUniqueFileName = new FileNameAndDigest(fileNameAndDigest.getFileName(), "digestOther");
        Assert.assertFalse(idempotentRepository.add(nonUniqueFileName.toString()));
    }

    @Test
    public void testNonUniqueDigestRejected() {
        idempotentRepository.clear();
        FileNameAndDigest fileNameAndDigest = new FileNameAndDigest("fileName", "digestOne");
        Assert.assertTrue(idempotentRepository.add(fileNameAndDigest.toString()));

        FileNameAndDigest nonUniqueDigest = new FileNameAndDigest("fileNameOther", fileNameAndDigest.getDigest());
        Assert.assertFalse(idempotentRepository.add(nonUniqueDigest.toString()));
    }

    @Test
    public void testNonUniqueFileNameAndDigestRejected() {
        idempotentRepository.clear();
        FileNameAndDigest fileNameAndDigest = new FileNameAndDigest("fileName", "digestOne");
        Assert.assertTrue(idempotentRepository.add(fileNameAndDigest.toString()));

        Assert.assertFalse(idempotentRepository.add(fileNameAndDigest.toString()));
    }

    @Test
    public void testUniqueFileNameAndDigestAccepted() {
        idempotentRepository.clear();
        FileNameAndDigest fileNameAndDigest = new FileNameAndDigest("fileName", "digestOne");
        Assert.assertTrue(idempotentRepository.add(fileNameAndDigest.toString()));

        FileNameAndDigest nonUniqueFileName = new FileNameAndDigest("fileNameOther", "digestOther");
        Assert.assertTrue(idempotentRepository.add(nonUniqueFileName.toString()));
    }


    @Test
    public void testRemoveEntryIfAddedRecently() {
        idempotentRepository.clear();
        FileNameAndDigest fileNameAndDigest = new FileNameAndDigest("fileName", "digestOne");
        Assert.assertTrue(idempotentRepository.add(fileNameAndDigest.toString()));

        Assert.assertTrue(idempotentRepository.remove(fileNameAndDigest.toString()));
        Assert.assertFalse(idempotentRepository.contains(fileNameAndDigest.toString()));
    }

    @Test
    public void testRemoveEntryIfAddedYesterday() {
        idempotentRepository.clear();
        FileNameAndDigest fileNameAndDigest = new FileNameAndDigest("fileName", "digestOne");

        idempotentRepository.insert(fileNameAndDigest.toString(), new Timestamp(DateUtils.addDays(new Date(), -1).getTime()));

        Assert.assertTrue(idempotentRepository.contains(fileNameAndDigest.toString()));

        Assert.assertFalse(idempotentRepository.remove(fileNameAndDigest.toString()));
        Assert.assertTrue(idempotentRepository.contains(fileNameAndDigest.toString()));
    }

}

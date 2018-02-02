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
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,classes = IdempotentRepositoryConfig.class, properties = "spring.main.sources=no.rutebanken.marduk.test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class UniqueDigestPerFilenameIdempotentRepositoryTest {


	@Autowired
	private UniqueDigestPerFileNameIdempotentRepository idempotentRepository;

	@Test
	public void testNonUniqueFileNameAndDigestCombinationIsRejected() {
		idempotentRepository.clear();
		FileNameAndDigest fileNameAndDigest = new FileNameAndDigest("fileName", "digestOne");
		Assert.assertTrue(idempotentRepository.add(fileNameAndDigest.toString()));

		Assert.assertFalse(idempotentRepository.add(fileNameAndDigest.toString()));
	}

	@Test
	public void testUniqueCombinationAllowed() {
		idempotentRepository.clear();
		FileNameAndDigest fileNameAndDigest1 = new FileNameAndDigest("fileNameOne", "digestOne");
		Assert.assertTrue(idempotentRepository.add(fileNameAndDigest1.toString()));
		FileNameAndDigest fileNameAndDigest2 = new FileNameAndDigest("fileNameTwo", "digestTwo");
		Assert.assertTrue(idempotentRepository.add(fileNameAndDigest2.toString()));


		FileNameAndDigest uniqueCombination = new FileNameAndDigest(fileNameAndDigest1.getFileName(), fileNameAndDigest2.getDigest());
		Assert.assertTrue(idempotentRepository.add(uniqueCombination.toString()));
	}

}

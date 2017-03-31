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
@SpringBootTest(classes = IdempotentRepositoryConfig.class, properties = "spring.main.sources=no.rutebanken.marduk.test")
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

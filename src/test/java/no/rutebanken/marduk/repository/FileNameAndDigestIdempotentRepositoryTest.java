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
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class FileNameAndDigestIdempotentRepositoryTest {

	@Autowired
	private FileNameAndDigestIdempotentRepository idempotentRepository;

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


}

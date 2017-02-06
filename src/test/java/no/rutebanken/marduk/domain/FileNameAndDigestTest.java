package no.rutebanken.marduk.domain;


import org.junit.Assert;
import org.junit.Test;

public class FileNameAndDigestTest {


	@Test
	public void testStringEncoding() {
		FileNameAndDigest org = new FileNameAndDigest("fileName", "digest");

		FileNameAndDigest converted = FileNameAndDigest.fromString(org.toString());

		Assert.assertEquals(org, converted);
		Assert.assertEquals(org.getFileName(), converted.getFileName());
		Assert.assertEquals(org.getDigest(), converted.getDigest());
	}
}

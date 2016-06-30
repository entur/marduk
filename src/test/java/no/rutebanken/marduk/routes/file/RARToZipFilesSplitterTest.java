package no.rutebanken.marduk.routes.file;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.commons.io.IOUtils;

import org.junit.Assert;
import org.junit.Test;

import com.github.junrar.exception.RarException;

public class RARToZipFilesSplitterTest {
	@Test
	public void testSplitSimpleRarFile() throws FileNotFoundException, IOException, RarException {
		checkSplitParts("src/test/resources/NRI 20160219.rar", 15);
	}

	@Test
	public void testSplitComplexRarFile() throws FileNotFoundException, IOException, RarException {
		checkSplitParts("src/test/resources/NRI 20160426.rar", 21);
	}
	
	@Test
	public void testSplitComplexRarFileWithError() throws FileNotFoundException, IOException, RarException {
		checkSplitParts("src/test/resources/NRI 20160603.rar", 2);
	}
	
	private void checkSplitParts(String filename, int numParts) throws IOException, RarException {
		CamelContext ctx = new DefaultCamelContext();
		Exchange ex = new DefaultExchange(ctx);

		byte[] data = IOUtils.toByteArray(new FileInputStream(filename));
		Assert.assertNotNull(data);

		List<Object> zipFiles = RARToZipFilesSplitter.splitRarFile(data, ex);
		Assert.assertNotNull(zipFiles);
		Assert.assertEquals("Number of files in file "+filename,numParts, zipFiles.size());
		
	}
}

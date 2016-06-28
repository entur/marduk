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
		CamelContext ctx = new DefaultCamelContext();
		Exchange ex = new DefaultExchange(ctx);

		byte[] data = IOUtils.toByteArray(new FileInputStream("src/test/resources/NRI 20160219.rar"));
		Assert.assertNotNull(data);

		List<Object> zipFiles = RARToZipFilesSplitter.splitRarFile(data, ex);
		Assert.assertNotNull(zipFiles);
		Assert.assertEquals(15, zipFiles.size());

	}

	@Test
	public void testSplitComplexRarFile() throws FileNotFoundException, IOException, RarException {
		CamelContext ctx = new DefaultCamelContext();
		Exchange ex = new DefaultExchange(ctx);

		byte[] data = IOUtils.toByteArray(new FileInputStream("src/test/resources/NRI 20160426.rar"));
		Assert.assertNotNull(data);

		List<Object> zipFiles = RARToZipFilesSplitter.splitRarFile(data, ex);
		Assert.assertNotNull(zipFiles);
		Assert.assertEquals(21, zipFiles.size());

	}
}

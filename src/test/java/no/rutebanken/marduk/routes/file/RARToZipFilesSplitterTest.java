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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

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
		assertNotNull(data);

		List<Object> zipFiles = RARToZipFilesSplitter.splitRarFile(data, ex);
		assertNotNull(zipFiles);
		assertThat(zipFiles.size()).as("Number of files in file "+filename).isEqualTo(numParts);
		
	}
}

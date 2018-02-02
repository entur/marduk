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

package no.rutebanken.marduk.routes.otp;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import no.rutebanken.marduk.Constants;

public class GraphBuilderProcessor implements Processor {

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	@Override
	public void process(Exchange exchange) throws Exception {
		File f = null;

		try {
			String otpGraphDirectory = exchange.getProperty(Constants.OTP_GRAPH_DIR, String.class);
			if (otpGraphDirectory == null || otpGraphDirectory.equals("")) {
				logger.warn("Empty otp graph directory string.");
				return;
			}
			f = new File(otpGraphDirectory);
			new GraphBuilderClient().buildGraph(f);
		} catch (RuntimeException e) {
			logger.warn("Got exception while trying to build new OTP graph.", e);
			if (f != null) {
				FileUtil.removeDir(f);
			}
			throw e;
		}
	}
}

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

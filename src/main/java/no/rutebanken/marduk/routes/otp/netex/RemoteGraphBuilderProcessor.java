package no.rutebanken.marduk.routes.otp.netex;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static no.rutebanken.marduk.Constants.OTP_GRAPH_DIR;
import static no.rutebanken.marduk.Constants.OTP_WORK_DIR;
import static no.rutebanken.marduk.Constants.TIMESTAMP;

public class RemoteGraphBuilderProcessor implements Processor {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private boolean buildBaseGraph = false;

    public RemoteGraphBuilderProcessor() {
    }

    public RemoteGraphBuilderProcessor(boolean buildBaseGraph) {
        this.buildBaseGraph = buildBaseGraph;
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        try {
            String otpGraphDirectory = exchange.getProperty(OTP_WORK_DIR, String.class);
            if (otpGraphDirectory == null || otpGraphDirectory.equals("")) {
                logger.warn("Empty otp graph directory string.");
                return;
            }

            String timestamp = exchange.getProperty(TIMESTAMP, String.class);


            new RemoteOtpGraphRunner(otpGraphDirectory, timestamp).runRemoteOtpGraphBuild();
        } catch (RuntimeException e) {
            logger.warn("Got exception while trying to build new OTP graph.", e);
            throw e;
        }
    }
}

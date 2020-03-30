package no.rutebanken.marduk.routes.otp;

import no.rutebanken.marduk.routes.otp.remote.OtpGraphBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static no.rutebanken.marduk.Constants.OTP_REMOTE_WORK_DIR;
import static no.rutebanken.marduk.Constants.TIMESTAMP;

/**
 * Camel processor that triggers the build process,
 */
public class RemoteGraphBuilderProcessor implements Processor {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private OtpGraphBuilder otpGraphBuilder;

    public RemoteGraphBuilderProcessor(OtpGraphBuilder otpGraphBuilder) {
        this.otpGraphBuilder = otpGraphBuilder;
    }

    @Override
    public void process(Exchange exchange) {

        try {
            String otpGraphDirectory = exchange.getProperty(OTP_REMOTE_WORK_DIR, String.class);
            if (otpGraphDirectory == null || otpGraphDirectory.isEmpty()) {
                logger.warn("Empty otp graph directory string.");
                return;
            }

            String timestamp = exchange.getProperty(TIMESTAMP, String.class);
            otpGraphBuilder.build(otpGraphDirectory, timestamp);

        } catch (RuntimeException e) {
            logger.warn("Got exception while trying to build new OTP graph.", e);
            throw e;
        }
    }
}

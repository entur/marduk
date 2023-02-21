package no.rutebanken.marduk.routes.otp;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static no.rutebanken.marduk.Constants.OTP_BUILD_CANDIDATE;
import static no.rutebanken.marduk.Constants.OTP_REMOTE_WORK_DIR;
import static no.rutebanken.marduk.Constants.TIMESTAMP;

/**
 * Camel processor that triggers the OTP graph build process,
 */
public class OtpGraphBuilderProcessor implements Processor {

    private static final Logger LOGGER = LoggerFactory.getLogger(OtpGraphBuilderProcessor.class);

    private final OtpGraphBuilder otpGraphBuilder;

    public OtpGraphBuilderProcessor(OtpGraphBuilder otpGraphBuilder) {
        this.otpGraphBuilder = otpGraphBuilder;
    }

    @Override
    public void process(Exchange exchange) {

        try {
            String otpGraphDirectory = exchange.getProperty(OTP_REMOTE_WORK_DIR, String.class);
            String timestamp = exchange.getProperty(TIMESTAMP, String.class);
            boolean candidate = exchange.getProperty(OTP_BUILD_CANDIDATE, Boolean.FALSE, Boolean.class);
            otpGraphBuilder.build(otpGraphDirectory, timestamp, candidate);
        } catch (RuntimeException e) {
            LOGGER.warn("Got exception while trying to build new OTP graph.", e);
            throw e;
        }
    }
}

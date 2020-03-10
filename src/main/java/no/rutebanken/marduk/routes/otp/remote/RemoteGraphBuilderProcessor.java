package no.rutebanken.marduk.routes.otp.remote;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.OTP_BUILD_BASE_GRAPH;
import static no.rutebanken.marduk.Constants.OTP_REMOTE_WORK_DIR;
import static no.rutebanken.marduk.Constants.TIMESTAMP;

/**
 * Camel processor that triggers the build process,
 */
@Component
public class RemoteGraphBuilderProcessor implements Processor {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private OtpGraphBuilder otpGraphBuilder;

    @Override
    public void process(Exchange exchange) {

        try {
            String otpGraphDirectory = exchange.getProperty(OTP_REMOTE_WORK_DIR, String.class);
            if (otpGraphDirectory == null || otpGraphDirectory.equals("")) {
                logger.warn("Empty otp graph directory string.");
                return;
            }

            boolean buildBaseGraph = exchange.getProperty(OTP_BUILD_BASE_GRAPH, Boolean.class);
            String timestamp = exchange.getProperty(TIMESTAMP, String.class);
            otpGraphBuilder.build(otpGraphDirectory, buildBaseGraph, timestamp);

        } catch (RuntimeException e) {
            logger.warn("Got exception while trying to build new OTP graph.", e);
            throw e;
        }
    }
}

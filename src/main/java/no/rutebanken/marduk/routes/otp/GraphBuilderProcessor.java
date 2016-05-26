package no.rutebanken.marduk.routes.otp;

import no.rutebanken.marduk.Constants;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class GraphBuilderProcessor implements Processor{

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void process(Exchange exchange) throws Exception {
        try {
            String otpGraphDirectory = exchange.getProperty(Constants.OTP_GRAPH_DIR, String.class);
            if (otpGraphDirectory == null || otpGraphDirectory.equals("")){
                logger.warn("Empty otp graph directory string.");
                return;
            }
            new GraphBuilderClient().buildGraph(new File(otpGraphDirectory));
        } catch (RuntimeException e){
            logger.warn("Got exception while trying to build new OTP graph.", e);
            throw e;
        }
    }
}

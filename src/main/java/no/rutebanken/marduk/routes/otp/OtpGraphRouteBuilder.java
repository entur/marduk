package no.rutebanken.marduk.routes.otp;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.http4.HttpMethods;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.PROVIDER_ID;

/**
 * Trigger graph
 */
@Component
public class OtpGraphRouteBuilder extends BaseRouteBuilder {

    @Value("${jenkins.url}")
    private String jenkinsUrl;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("activemq:queue:OtpGraphQueue")
                .log(LoggingLevel.INFO, getClass().getName(), "Triggering OTP graph building on '" + jenkinsUrl + "/buildWithParameters?CORRELATION_ID=${header." + PROVIDER_ID + "}'")
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .toD(jenkinsUrl + "/buildWithParameters?CORRELATION_ID=${header." + PROVIDER_ID + "}") //TODO figure out a proper id to use
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.INFO, getClass().getName(), "OTP graph building triggered.");


        //Posting to queue from command line:        curl -XPOST -d "ID:2,RESULT:OK" http://admin:admin@localhost:8161/api/message?destination=queue://queue:OtpGraphStatusQueue
        from("activemq:queue:OtpGraphStatusQueue")
                .log(LoggingLevel.INFO, getClass().getName(), "Got OTP graph building status.")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true");

    }
}

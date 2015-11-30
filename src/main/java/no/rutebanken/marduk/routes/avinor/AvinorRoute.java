package no.rutebanken.marduk.routes.avinor;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Downloads data from Avinor periodically. Currently OSL.
 * Example query: http://flydata.avinor.no/XmlFeed.asp?TimeFrom=1&TimeTo=7&airport=OSL&direction=D&lastUpdate=2009-03-10T15:03:00Z
 */
//@Component
public class AvinorRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        from("quartz2://backend/avinor?cron=0+0/3+*+*+*+?")
                .log("Starting avinor data import.")
                .to("http4://flydata.avinor.no/XmlFeed.asp?TimeFrom=0&airport=OSL")
                    .streamCaching()
                    .to("log:no.rutebanken.marduk.routes.avinor.AvinorRoute?showAll=true&multiline=true&showStreams=true&showBody=true&showOut=true&maxChars=1000000000")
                .doTry()
                    .to("validator:no/rutebanken/marduk/routes/avinor/XmlFeed.xsd")
                    .log("Successfully validated avinor flight data")
                    .to("activemq:queue:AvinorQueue")
                .doCatch(org.apache.camel.ValidationException.class)
                    .log("Could not validate avinor flight data")
                    .to("log:no.rutebanken.marduk.routes.avinor.AvinorRoute?showAll=true&multiline=true&showStreams=true&showBody=true&showOut=true&maxChars=1000000000")
                .end();

    }

}

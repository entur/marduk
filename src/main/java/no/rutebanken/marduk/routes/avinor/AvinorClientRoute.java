package no.rutebanken.marduk.routes.avinor;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Reads map data from topic
 */
@Component
public class AvinorClientRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        from("activemq:queue:AvinorQueue")
                .log("Received JMS message on avinor test client.")
                .to("file:target/files/test/avinor")
                .log("JMS message stored on avinor test client.");

    }

}

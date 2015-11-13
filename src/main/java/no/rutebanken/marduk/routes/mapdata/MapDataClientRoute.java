package no.rutebanken.marduk.routes.mapdata;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Reads map data from topic
 */
@Component
public class MapDataClientRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        from("activemq:topic:MapDataTopic")
                .log("Received JMS message on test client.")
                .to("file:target/files/test/mapdata")
                .log("JMS message stored on test client.");

    }

}

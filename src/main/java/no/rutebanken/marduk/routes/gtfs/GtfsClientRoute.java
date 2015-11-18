package no.rutebanken.marduk.routes.gtfs;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Pushes file to lamassu
 */
@Component
public class GtfsClientRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        //TODO for testing only
        from("file:target/files/test/gtfs")
                .to("sftp://nvdb@lamassu:22?privateKeyFile=/opt/jboss/.ssh/lamassu.pem");

    }

}

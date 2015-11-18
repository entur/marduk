package no.rutebanken.marduk.routes.gtfs;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Downloads file from lamassu
 */
@Component
public class GtfsRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        from("sftp://nvdb@lamassu:22?privateKeyFile=/opt/jboss/.ssh/lamassu.pem&delay=30s&delete=true")
                .to("file:target/files/input/gtfs");

    }

}

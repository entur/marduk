package no.rutebanken.marduk.routes.sftp;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Pushes file to lamassu
 */
@Component
public class SftpClientRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        //TODO for testing only
//        from("file:target/files/input/gtfs")
//                .to("sftp://nvdb@lamassu:22?privateKeyFile=/opt/jboss/.ssh/lamassu.pem");

        from("activemq:queue:ProcessGtfsQueue")
                .to("file:target/files/fromqueue/ProcessGtfsQueue");

        from("activemq:queue:DeadLetterQueue")
                .to("file:target/files/fromqueue/DeadLetterQueue");

    }

}

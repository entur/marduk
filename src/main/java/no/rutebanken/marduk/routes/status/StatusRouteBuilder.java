package no.rutebanken.marduk.routes.status;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class StatusRouteBuilder extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("direct:updateStatus")
        .to("activemq:topic:ExternalProviderStatus");
    }

}

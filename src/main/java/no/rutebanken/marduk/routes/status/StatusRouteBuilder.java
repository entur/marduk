package no.rutebanken.marduk.routes.status;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class StatusRouteBuilder extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("direct:updateStatus")
                .log(LoggingLevel.INFO, getClass().getName(), "Sending off status: ${body}")
        .to("activemq:queue:ExternalProviderStatus")
        .routeId("update-status").startupOrder(1);
    }

}

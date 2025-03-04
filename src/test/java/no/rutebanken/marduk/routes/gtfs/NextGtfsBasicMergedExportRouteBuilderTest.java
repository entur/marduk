package no.rutebanken.marduk.routes.gtfs;


import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NextGtfsBasicMergedExportRouteBuilderTest extends MardukRouteBuilderIntegrationTestBase {

    @Produce("direct:exportMergedGtfsNext")
    protected ProducerTemplate exportMergedGtfsNextRoute;

    @EndpointInject("mock:damuAggregateGtfsQueue")
    protected MockEndpoint damuAggregateGtfsMockEndpoint;

    @Test
    public void testBasicRoute() throws Exception {
        AdviceWith
                .adviceWith(context,
            "gtfs-export-merged-route-next",
            a -> a
                    .weaveById("damuAggregateGtfsNext")
                    .replace()
                    .to("mock:damuAggregateGtfsQueue")
                );

        String correlationHeaderValue = UUID.randomUUID().toString();
        Map<String, String> headers = new HashMap<>();
        headers.put(Constants.CORRELATION_ID, correlationHeaderValue);

        damuAggregateGtfsMockEndpoint.setExpectedMessageCount(1);
        damuAggregateGtfsMockEndpoint.expectedHeaderReceived("CamelGooglePubsubAttributes", headers);

        context.start();
        sendBodyAndHeadersToPubSub(exportMergedGtfsNextRoute, "", headers);
        damuAggregateGtfsMockEndpoint.assertIsSatisfied();
    }
}

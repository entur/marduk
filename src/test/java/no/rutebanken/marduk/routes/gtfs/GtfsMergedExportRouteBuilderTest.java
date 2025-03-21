package no.rutebanken.marduk.routes.gtfs;

import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import java.util.*;

public class GtfsMergedExportRouteBuilderTest extends MardukRouteBuilderIntegrationTestBase {
    @Produce("google-pubsub:{{marduk.pubsub.project.id}}:GtfsExportMergedQueue")
    private ProducerTemplate gtfsExportMergedQueueProducerTemplate;

    @EndpointInject("mock:exportMergedGtfs")
    protected MockEndpoint exportMergedGtfsRouteMock;

    @Test
    public void testExportGtfsExtendedMergedRoute() throws Exception {
        AdviceWith
                .adviceWith(context,
                        "gtfs-extended-export-merged-route",
                        a -> a
                                .weaveByToUri("direct:exportMergedGtfs")
                                .replace()
                                .to("mock:exportMergedGtfs")
                );
        exportMergedGtfsRouteMock.expectedMessageCount(1);
        context.start();
        sendBodyAndHeadersToPubSub(gtfsExportMergedQueueProducerTemplate, "test", new HashMap<>());
        exportMergedGtfsRouteMock.assertIsSatisfied();
    }
}

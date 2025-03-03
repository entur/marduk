package no.rutebanken.marduk.routes.gtfs;

import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import java.util.*;

public class GtfsExtendedMergedExportRouteBuilderTest extends MardukRouteBuilderIntegrationTestBase {
    @Produce("google-pubsub:{{marduk.pubsub.project.id}}:GtfsExportMergedQueue")
    private ProducerTemplate producerTemplate;

    @EndpointInject("mock:exportGtfsExtendedMergedNext")
    protected MockEndpoint nextMergedRouteMock;

    @EndpointInject("mock:exportGtfsExtendedMerged")
    protected MockEndpoint oldMergedRouteMock;

    @Test
    public void testDamuGtfsAggregationTrigger() throws Exception {
        System.setProperty("DAMU_GTFS_AGGREGATION", "true");
        AdviceWith
            .adviceWith(context,
                    "gtfs-extended-export-merged-route",
                    a -> a
                            .weaveByToUri("direct:exportGtfsExtendedMergedNext")
                            .replace()
                            .to("mock:exportGtfsExtendedMergedNext")
            );

        context.start();
        sendBodyAndHeadersToPubSub(producerTemplate, "test", new HashMap<>());
        nextMergedRouteMock.expectedMessageCount(1);
        nextMergedRouteMock.assertIsSatisfied();
    }

    @Test
    public void testOldGtfsAggregationTrigger() throws Exception {
        System.setProperty("DAMU_GTFS_AGGREGATION", "false");
        AdviceWith
                .adviceWith(context,
                        "gtfs-extended-export-merged-route",
                        a -> a
                                .weaveByToUri("direct:exportGtfsExtendedMerged")
                                .replace()
                                .to("mock:exportGtfsExtendedMerged")
                );

        context.start();
        sendBodyAndHeadersToPubSub(producerTemplate, "test", new HashMap<>());
        oldMergedRouteMock.expectedMessageCount(1);
        oldMergedRouteMock.assertIsSatisfied();
    }
}

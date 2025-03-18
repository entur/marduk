package no.rutebanken.marduk.routes.gtfs;

import no.rutebanken.marduk.Constants;
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
    private ProducerTemplate gtfsExportMergedQueueProducerTemplate;

    @Produce("direct:exportGtfsExtendedMerged")
    private ProducerTemplate exportGtfsExtendedMergedProducerTemplate;

    @EndpointInject("mock:exportGtfsExtendedMerged")
    protected MockEndpoint mergedRouteMock;

    @EndpointInject("mock:exportMergedGtfs")
    protected MockEndpoint exportMergedGtfsRouteMock;

    @Test
    public void testDamuGtfsAggregationTrigger() throws Exception {
        AdviceWith
            .adviceWith(context,
                    "gtfs-extended-export-merged-route",
                    a -> a
                            .weaveByToUri("direct:exportGtfsExtendedMerged")
                            .replace()
                            .to("mock:exportGtfsExtendedMerged")
            );

        mergedRouteMock.expectedMessageCount(1);
        context.start();
        sendBodyAndHeadersToPubSub(gtfsExportMergedQueueProducerTemplate, "test", new HashMap<>());
        mergedRouteMock.assertIsSatisfied();
    }

    @Test
    public void testExportGtfsExtendedMergedRoute() throws Exception {
        AdviceWith
                .adviceWith(context,
                        "gtfs-extended-export-merged",
                        a -> a
                                .weaveByToUri("direct:exportMergedGtfs")
                                .replace()
                                .to("mock:exportMergedGtfs")
                );
        exportMergedGtfsRouteMock.expectedHeaderReceived(Constants.FILE_NAME, "rb_norway-aggregated-gtfs.zip");
        exportMergedGtfsRouteMock.expectedMessageCount(1);
        context.start();
        sendBodyAndHeadersToPubSub(exportGtfsExtendedMergedProducerTemplate, "test", new HashMap<>());
        exportMergedGtfsRouteMock.assertIsSatisfied();
    }
}

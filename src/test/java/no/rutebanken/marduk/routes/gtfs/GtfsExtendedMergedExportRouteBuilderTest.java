package no.rutebanken.marduk.routes.gtfs;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.apache.camel.builder.Builder.constant;

public class GtfsExtendedMergedExportRouteBuilderTest extends MardukRouteBuilderIntegrationTestBase {
    @Produce("google-pubsub:{{marduk.pubsub.project.id}}:GtfsExportMergedQueue")
    private ProducerTemplate gtfsExportMergedQueueProducerTemplate;

    @Produce("direct:exportGtfsExtendedMerged")
    private ProducerTemplate exportGtfsExtendedMergedProducerTemplate;

    @Produce("direct:exportGtfsExtendedMergedNext")
    private ProducerTemplate exportGtfsExtendedMergedNextProducerTemplate;

    @EndpointInject("mock:exportGtfsExtendedMergedNext")
    protected MockEndpoint nextMergedRouteMock;

    @EndpointInject("mock:exportGtfsExtendedMerged")
    protected MockEndpoint oldMergedRouteMock;

    @EndpointInject("mock:exportMergedGtfsNext")
    protected MockEndpoint exportMergedGtfsNextRouteMock;

    @EndpointInject("mock:exportMergedGtfs")
    protected MockEndpoint exportMergedGtfsRouteMock;

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
        sendBodyAndHeadersToPubSub(gtfsExportMergedQueueProducerTemplate, "test", new HashMap<>());
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
        sendBodyAndHeadersToPubSub(gtfsExportMergedQueueProducerTemplate, "test", new HashMap<>());
        oldMergedRouteMock.expectedMessageCount(1);
        oldMergedRouteMock.assertIsSatisfied();
    }

    @Test
    public void testExportGtfsExtendedMergedNextRoute() throws Exception {
        AdviceWith
                .adviceWith(context,
                        "gtfs-extended-export-merged-next",
                        a -> a
                                .weaveByToUri("direct:exportMergedGtfsNext")
                                .replace()
                                .to("mock:exportMergedGtfsNext")
                );
        context.start();
        sendBodyAndHeadersToPubSub(exportGtfsExtendedMergedNextProducerTemplate, "test", new HashMap<>());
        exportMergedGtfsNextRouteMock.expectedMessageCount(1);
        exportMergedGtfsRouteMock.expectedHeaderReceived(Constants.FILE_NAME, "rb_norway-aggregated-gtfs. zip");
        exportMergedGtfsRouteMock.expectedHeaderReceived(Constants.JOB_ACTION, constant(JobEvent.TimetableAction.EXPORT_GTFS_MERGED.name()));
        exportMergedGtfsNextRouteMock.assertIsSatisfied();
    }

    @Test
    public void testOldExportGtfsExtendedMergedRoute() throws Exception {
        AdviceWith
                .adviceWith(context,
                        "gtfs-extended-export-merged",
                        a -> a
                                .weaveByToUri("direct:exportMergedGtfs")
                                .replace()
                                .to("mock:exportMergedGtfs")
                );
        context.start();
        sendBodyAndHeadersToPubSub(exportGtfsExtendedMergedProducerTemplate, "test", new HashMap<>());
        exportMergedGtfsRouteMock.expectedMessageCount(1);
        exportMergedGtfsRouteMock.expectedHeaderReceived(Constants.FILE_NAME, "rb_norway-aggregated-gtfs. zip");
        exportMergedGtfsRouteMock.expectedHeaderReceived(Constants.JOB_ACTION, constant(JobEvent.TimetableAction.EXPORT_GTFS_MERGED.name()));
        exportMergedGtfsRouteMock.assertIsSatisfied();
    }
}

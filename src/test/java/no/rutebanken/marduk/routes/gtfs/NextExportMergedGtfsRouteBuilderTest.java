package no.rutebanken.marduk.routes.gtfs;


import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static no.rutebanken.marduk.Constants.JOB_ACTION;

public class NextExportMergedGtfsRouteBuilderTest extends MardukRouteBuilderIntegrationTestBase {

    @Produce("direct:exportMergedGtfs")
    protected ProducerTemplate exportMergedGtfsRoute;

    @EndpointInject("mock:damuAggregateGtfsQueue")
    protected MockEndpoint damuAggregateGtfsMockEndpoint;

    @Produce("google-pubsub:{{marduk.pubsub.project.id}}:MardukAggregateGtfsStatusQueue")
    protected ProducerTemplate mardukAggregateGtfsStatusQueueRoute;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatusMockEndpoint;

    @Test
    public void testExportMergedGtfsNextRoute() throws Exception {
        AdviceWith
                .adviceWith(context,
            "gtfs-export-merged-route",
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
        sendBodyAndHeadersToPubSub(exportMergedGtfsRoute, "", headers);
        damuAggregateGtfsMockEndpoint.assertIsSatisfied();
    }

    @Test
    public void testAggregateGtfsStatusRouteWithStatusStarted() throws Exception {
        AdviceWith
                .adviceWith(context,
                        "gtfs-aggregate-status-route",
                        a -> a
                                .weaveByToUri("direct:updateStatus")
                                .replace()
                                .to("mock:updateStatus")
                );

        Map<String, String> headers = new HashMap<>();
        headers.put("status", "started");
        headers.put(JOB_ACTION, "action");

        ObjectMapper mapper = new ObjectMapper();

        updateStatusMockEndpoint.setExpectedMessageCount(1);
        updateStatusMockEndpoint.whenAnyExchangeReceived(exchange -> {
            Map<String, String> attributes = exchange.getIn().getHeader("CamelGooglePubsubAttributes", Map.class);
            Assertions.assertEquals("started", attributes.get("status"));
            Assertions.assertEquals("action", attributes.get(JOB_ACTION));
            
            String systemStatusValue = exchange.getIn().getHeader("RutebankenSystemStatus", String.class);
            Map<String, Object> statusValues = mapper.readValue(systemStatusValue, Map.class);
            Assertions.assertNotNull(statusValues.get("correlationId"));
            Assertions.assertEquals(statusValues.get("domain").toString(), JobEvent.JobDomain.TIMETABLE_PUBLISH.toString());
            Assertions.assertEquals(statusValues.get("state").toString(), JobEvent.State.STARTED.toString());
        });

        context.start();
        sendBodyAndHeadersToPubSub(mardukAggregateGtfsStatusQueueRoute, "test", headers);
        updateStatusMockEndpoint.assertIsSatisfied();
    }

    @Test
    public void testAggregateGtfsStatusRouteWithStatusOk() throws Exception {
        AdviceWith
                .adviceWith(context,
                        "gtfs-aggregate-status-route",
                        a -> a
                                .weaveByToUri("direct:updateStatus")
                                .replace()
                                .to("mock:updateStatus")
                                .process(exchange -> {
                                    System.out.println(exchange.getIn().getBody(String.class));
                                })
                );

        Map<String, String> headers = new HashMap<>();
        headers.put("status", "ok");
        headers.put(JOB_ACTION, "action");
        String correlationId = UUID.randomUUID().toString();
        headers.put(Constants.CORRELATION_ID, correlationId);

        ObjectMapper mapper = new ObjectMapper();

        updateStatusMockEndpoint.setExpectedMessageCount(1);
        updateStatusMockEndpoint.whenAnyExchangeReceived(exchange -> {
            Map<String, String> attributes = exchange.getIn().getHeader("CamelGooglePubsubAttributes", Map.class);
            Assertions.assertEquals("ok", attributes.get("status"));
            Assertions.assertEquals("action", attributes.get(JOB_ACTION));
            Assertions.assertEquals(correlationId, attributes.get(Constants.CORRELATION_ID));
            
            String systemStatusValue = exchange.getIn().getHeader("RutebankenSystemStatus", String.class);
            Map<String, Object> statusValues = mapper.readValue(systemStatusValue, Map.class);
            Assertions.assertEquals(statusValues.get("correlationId"), correlationId);
            Assertions.assertEquals(statusValues.get("domain").toString(), JobEvent.JobDomain.TIMETABLE_PUBLISH.toString());
            Assertions.assertEquals(statusValues.get("state").toString(), JobEvent.State.OK.toString());
        });

        context.start();
        sendBodyAndHeadersToPubSub(mardukAggregateGtfsStatusQueueRoute, "test", headers);
        updateStatusMockEndpoint.assertIsSatisfied();
    }

    @Test
    public void testAggregateGtfsStatusRouteWithStatusFailed() throws Exception {
        AdviceWith
                .adviceWith(context,
                        "gtfs-aggregate-status-route",
                        a -> a
                                .weaveByToUri("direct:updateStatus")
                                .replace()
                                .to("mock:updateStatus")
                                .process(exchange -> {
                                    System.out.println(exchange.getIn().getBody(String.class));
                                })
                );

        Map<String, String> headers = new HashMap<>();
        headers.put("status", "failed");
        headers.put(JOB_ACTION, "action");
        String correlationId = UUID.randomUUID().toString();
        headers.put(Constants.CORRELATION_ID, correlationId);

        ObjectMapper mapper = new ObjectMapper();

        updateStatusMockEndpoint.setExpectedMessageCount(1);
        updateStatusMockEndpoint.whenAnyExchangeReceived(exchange -> {
            Map<String, String> attributes = exchange.getIn().getHeader("CamelGooglePubsubAttributes", Map.class);
            Assertions.assertEquals("failed", attributes.get("status"));
            Assertions.assertEquals("action", attributes.get(JOB_ACTION));
            Assertions.assertEquals(correlationId, attributes.get(Constants.CORRELATION_ID));
            
            String systemStatusValue = exchange.getIn().getHeader("RutebankenSystemStatus", String.class);
            Map<String, Object> statusValues = mapper.readValue(systemStatusValue, Map.class);
            Assertions.assertEquals(statusValues.get("correlationId"), correlationId);
            Assertions.assertEquals(statusValues.get("domain").toString(), JobEvent.JobDomain.TIMETABLE_PUBLISH.toString());
            Assertions.assertEquals(statusValues.get("state").toString(), JobEvent.State.FAILED.toString());
        });

        context.start();
        sendBodyAndHeadersToPubSub(mardukAggregateGtfsStatusQueueRoute, "test", headers);
        updateStatusMockEndpoint.assertIsSatisfied();
    }
}

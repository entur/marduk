/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.routes;

import com.google.cloud.pubsub.v1.stub.SubscriberStub;
import com.google.pubsub.v1.ProjectSubscriptionName;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.repository.ProviderRepository;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.pubsub.GooglePubsubConstants;
import org.apache.camel.component.google.pubsub.GooglePubsubEndpoint;
import org.apache.camel.component.google.pubsub.consumer.AcknowledgeSync;
import org.apache.camel.component.hazelcast.policy.HazelcastRoutePolicy;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.Synchronization;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.quartz.CronExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import org.springframework.cloud.gcp.pubsub.support.BasicAcknowledgeablePubsubMessage;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static no.rutebanken.marduk.Constants.SINGLETON_ROUTE_DEFINITION_GROUP_NAME;


/**
 * Defines common route behavior.
 */
public abstract class BaseRouteBuilder extends RouteBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseRouteBuilder.class);

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private PubSubTemplate pubSubTemplate;


    @Value("${quartz.lenient.fire.time.ms:180000}")
    private int lenientFireTimeMs;

    @Value("${marduk.camel.redelivery.max:3}")
    private int maxRedelivery;

    @Value("${marduk.camel.redelivery.delay:5000}")
    private int redeliveryDelay;

    @Value("${marduk.camel.redelivery.backoff.multiplier:3}")
    private int backOffMultiplier;


    @Override
    public void configure() throws Exception {
        errorHandler(defaultErrorHandler()
                .redeliveryDelay(redeliveryDelay)
                .maximumRedeliveries(maxRedelivery)
                .onRedelivery(this::logRedelivery)
                .useExponentialBackOff()
                .backOffMultiplier(backOffMultiplier)
                .logExhausted(true)
                .logRetryStackTrace(true));

        interceptFrom("google-pubsub:*")
                .process(exchange ->
                {
                    Map<String, String> pubSubAttributes = exchange.getIn().getHeader(GooglePubsubConstants.ATTRIBUTES, Map.class);
                    pubSubAttributes.entrySet().stream().filter(entry -> !entry.getKey().startsWith("CamelGooglePubsub")).forEach(entry -> exchange.getIn().setHeader(entry.getKey(), entry.getValue()));
                });

        interceptSendToEndpoint("google-pubsub:*").process(
                exchange -> {
                    Map<String, String> pubSubAttributes = new HashMap<>();
                    exchange.getIn().getHeaders().entrySet().stream()
                            .filter(entry -> !entry.getKey().startsWith("CamelGooglePubsub"))
                            .filter(entry -> Objects.toString(entry.getValue()).length() <= 1024)
                            .forEach(entry -> pubSubAttributes.put(entry.getKey(), Objects.toString(entry.getValue(), "")));
                    exchange.getIn().setHeader(GooglePubsubConstants.ATTRIBUTES, pubSubAttributes);

                });

    }

    protected void logRedelivery(Exchange exchange) {
        int redeliveryCounter = exchange.getIn().getHeader("CamelRedeliveryCounter", Integer.class);
        int redeliveryMaxCounter = exchange.getIn().getHeader("CamelRedeliveryMaxCounter", Integer.class);
        Throwable camelCaughtThrowable = exchange.getProperty("CamelExceptionCaught", Throwable.class);
        Throwable rootCause = ExceptionUtils.getRootCause(camelCaughtThrowable);

        String rootCauseType = rootCause != null ? rootCause.getClass().getName() : "";
        String rootCauseMessage = rootCause != null ? rootCause.getMessage() : "";

        log.warn("Exchange failed ({}: {}) . Redelivering the message locally, attempt {}/{}...", rootCauseType, rootCauseMessage, redeliveryCounter, redeliveryMaxCounter);
    }

    protected String logDebugShowAll() {
        return "log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true";
    }

    /**
     * Add ACK/NACK completion callback for an aggregated exchange.
     * The callback should be added after the aggregation is complete to prevent individual messages from being acked
     * by the aggregator.
     */
    protected void addOnCompletionForAggregatedExchange(Exchange exchange) throws IOException {
        GooglePubsubEndpoint googlePubsubEndpoint = (GooglePubsubEndpoint) exchange.getFromEndpoint();
        SubscriberStub subscriber = googlePubsubEndpoint.getComponent().getSubscriberStub();
        String subscriptionName = ProjectSubscriptionName.format(googlePubsubEndpoint.getProjectId(), googlePubsubEndpoint.getDestinationName());
        if (googlePubsubEndpoint.isSynchronousPull()) {
            LOGGER.info("Add call back for synchronous pull to {}", subscriptionName);
            exchange.adapt(ExtendedExchange.class).addOnCompletion(new MardukAcknowledgeSync(subscriber, subscriptionName));
        } else {
            LOGGER.info("Asynchronous pull");
            throw new IllegalStateException("Cannot add completion callback for Asynchronous pull");
            //exchange.adapt(ExtendedExchange.class).addOnCompletion(new AcknowledgeAsync(ackReplyConsumer));
        }


    }


    private static class AckSynchronization implements Synchronization {

        private final List<BasicAcknowledgeablePubsubMessage> ackList;

        public AckSynchronization(List<BasicAcknowledgeablePubsubMessage> ackList) {
            this.ackList = ackList;
        }

        @Override
        public void onComplete(Exchange exchange) {
            ackList.forEach(BasicAcknowledgeablePubsubMessage::ack);
        }

        @Override
        public void onFailure(Exchange exchange) {
            ackList.forEach(BasicAcknowledgeablePubsubMessage::nack);
        }
    }


    protected ProviderRepository getProviderRepository() {
        return providerRepository;
    }

    protected void setNewCorrelationId(Exchange e) {
        e.getIn().setHeader(Constants.CORRELATION_ID, UUID.randomUUID().toString());
    }

    protected void setCorrelationIdIfMissing(Exchange e) {
        e.getIn().setHeader(Constants.CORRELATION_ID, e.getIn().getHeader(Constants.CORRELATION_ID, UUID.randomUUID().toString()));
    }

    protected String correlation() {
        return "[providerId=${header." + Constants.PROVIDER_ID + "} referential=${header." + Constants.CHOUETTE_REFERENTIAL + "} correlationId=${header." + Constants.CORRELATION_ID + "}] ";
    }

    protected void removeAllCamelHeaders(Exchange e) {
        e.getIn().removeHeaders(Constants.CAMEL_ALL_HEADERS, GooglePubsubConstants.ACK_ID);

    }

    protected void removeAllCamelHttpHeaders(Exchange e) {
        e.getIn().removeHeaders(Constants.CAMEL_ALL_HTTP_HEADERS, GooglePubsubConstants.ACK_ID);
    }

    /**
     * Create a new singleton route definition from URI. Only one such route should be active throughout the cluster at any time.
     */
    protected RouteDefinition singletonFrom(String uri) {
        return this.from(uri).group(SINGLETON_ROUTE_DEFINITION_GROUP_NAME);
    }


    /**
     * Quartz should only trigger if singleton route is started, this node is the cluster leader for the route and fireTime is (almost) same as scheduledFireTime.
     * <p>
     * To avoid multiple firings in cluster and re-firing as route is resumed upon change of leadership.
     */
    protected boolean shouldQuartzRouteTrigger(Exchange e, String cron) {
        CronExpression cronExpression;
        String cleanCron = cron.replace("+", " ");
        try {
            cronExpression = new CronExpression(cleanCron);
        } catch (ParseException pe) {
            throw new MardukException("Invalid cron: " + cleanCron, pe);
        }
        return isStarted(e.getFromRouteId()) && isLeader(e.getFromRouteId()) && isScheduledQuartzFiring(e, cronExpression);
    }

    private boolean isScheduledQuartzFiring(Exchange exchange, CronExpression cron) {
        Date now = new Date();
        Date scheduledFireTime = cron.getNextValidTimeAfter(DateUtils.addMilliseconds(now, -lenientFireTimeMs));
        boolean isScheduledFiring = scheduledFireTime.before(now);

        if (!isScheduledFiring) {
            log.warn("Ignoring quartz trigger for route:{} as this is probably not a match for cron expression: {}", exchange.getFromRouteId(), cron.getCronExpression());
        }
        return isScheduledFiring;
    }

    protected boolean isStarted(String routeId) {
        ServiceStatus status = getContext().getRouteController().getRouteStatus(routeId);
        return status != null && status.isStarted();
    }

    protected boolean isLeader(String routeId) {
        List<RoutePolicy> routePolicyList = getContext().getRoute(routeId).getRoutePolicyList();
        if (routePolicyList != null) {
            for (RoutePolicy routePolicy : routePolicyList) {
                if (routePolicy instanceof HazelcastRoutePolicy) {
                    return ((HazelcastRoutePolicy) (routePolicy)).isLeader();
                }
            }
        }
        return false;
    }

    protected void deleteDirectoryRecursively(String directory) {

        log.debug("Deleting local directory {} ...", directory);
        try {
            Path pathToDelete = Paths.get(directory);
            boolean deleted = FileSystemUtils.deleteRecursively(pathToDelete);
            if (deleted) {
                log.debug("Local directory {} cleanup done.", directory);
            } else {
                log.debug("The directory {} did not exist, ignoring deletion request", directory);
            }
        } catch (IOException e) {
            log.warn("Failed to delete directory {}", directory, e);
        }
    }

}

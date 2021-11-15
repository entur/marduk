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

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.aggregation.IdleRouteAggregationMonitor;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Message;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.pubsub.GooglePubsubConstants;
import org.apache.camel.component.google.pubsub.consumer.AcknowledgeAsync;
import org.apache.camel.component.master.MasterConsumer;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.support.DefaultExchange;
import org.apache.commons.lang3.time.DateUtils;
import org.quartz.CronExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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


/**
 * Defines common route behavior.
 */
public abstract class BaseRouteBuilder extends RouteBuilder {

    private static final String SYNCHRONIZATION_HOLDER = "SYNCHRONIZATION_HOLDER";

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    protected IdleRouteAggregationMonitor idleRouteAggregationMonitor;

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

        // Copy all PubSub headers except the internal Camel PubSub headers from the PubSub message into the Camel message headers.
        interceptFrom("google-pubsub:*")
                .process(exchange ->
                {
                    Map<String, String> pubSubAttributes = exchange.getIn().getHeader(GooglePubsubConstants.ATTRIBUTES, Map.class);
                    if (pubSubAttributes == null) {
                        throw new IllegalStateException("Missing PubSub attribute maps in Exchange");
                    }
                    pubSubAttributes.entrySet()
                            .stream()
                            .filter(entry -> !entry.getKey().startsWith("CamelGooglePubsub"))
                            .forEach(entry -> exchange.getIn().setHeader(entry.getKey(), entry.getValue()));
                });

        // Copy all PubSub headers except the internal Camel PubSub headers from the Camel message into the PubSub message.
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
        String correlation = simple(correlation(), String.class).evaluate(exchange, String.class);

        log.warn("{} Exchange failed, redelivering the message locally, attempt {}/{}...", correlation, redeliveryCounter, redeliveryMaxCounter, camelCaughtThrowable);
    }

    protected String logDebugShowAll() {
        return "log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true";
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
        String lockName = getMasterLockName(uri);
        return this.from("master:" + lockName + ':' + uri);
    }

    /**
     * Create a lock name for an endpoint URI. The lock name should be unique across the Camel context so that each
     * route gets its own lock.
     * When using a file-based implementation for the camel-master lock (for local testing), the lock is created as a file in the local file system.
     * Thus the lock name should be a valid file name.
     * The lock name is built by stripping the component type (example: "google-pubsub:") and the endpoint parameters.
     * (example: "?synchronousPull=true")
     *
     * @param uri the endpoint URI
     * @return a lock name
     */
    private String getMasterLockName(String uri) {
        if (uri.indexOf('?') != -1) {
            return uri.substring(uri.lastIndexOf(':') + 1, uri.indexOf('?'));
        }
        return uri.substring(uri.lastIndexOf(':') + 1);
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
        Consumer consumer = getContext().getRoute(routeId).getConsumer();
        if (consumer instanceof MasterConsumer) {
            return ((MasterConsumer) consumer).isMaster();
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

    /**
     * Remove the PubSub synchronization.
     * This prevents an aggregator from acknowledging the aggregated PubSub messages before the end of the route.
     * In case of failure during the routing this would make it impossible to retry the messages.
     * The synchronization is stored temporarily in a header and is applied again after the aggregation is complete
     *
     * @param e
     * @see #addSynchronizationForAggregatedExchange(Exchange)
     */
    public void removeSynchronizationForAggregatedExchange(Exchange e) {
        DefaultExchange temporaryExchange = new DefaultExchange(e.getContext());
        e.getUnitOfWork().handoverSynchronization(temporaryExchange, AcknowledgeAsync.class::isInstance);
        e.getIn().setHeader(SYNCHRONIZATION_HOLDER, temporaryExchange);
    }

    /**
     * Add back the PubSub synchronization.
     *
     * @see #removeSynchronizationForAggregatedExchange(Exchange)
     */
    protected void addSynchronizationForAggregatedExchange(Exchange aggregatedExchange) {
        List<Message> messages = aggregatedExchange.getIn().getBody(List.class);
        for (Message m : messages) {
            Exchange temporaryExchange = m.getHeader(SYNCHRONIZATION_HOLDER, Exchange.class);
            if (temporaryExchange == null) {
                throw new IllegalStateException("Synchronization holder not found");
            }
            temporaryExchange.adapt(ExtendedExchange.class).handoverCompletions(aggregatedExchange);
        }
    }

}

package no.rutebanken.marduk.gtfs;

import no.rutebanken.marduk.batch.BatchRunner;
import no.rutebanken.marduk.batch.BatchedRequests;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubPublisher;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.repository.ProviderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.CURRENT_AGGREGATED_GTFS_FILENAME;
import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.ET_CLIENT_NAME_HEADER;
import static no.rutebanken.marduk.Constants.GTFS_ROUTE_DISPATCHER_AGGREGATION_HEADER_VALUE;
import static no.rutebanken.marduk.Constants.GTFS_ROUTE_DISPATCHER_HEADER_NAME;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

/**
 * Asks damu to merge every provider's GTFS export into the national dataset.
 *
 * <p>Replaces {@code direct:exportMergedGtfs} and {@code direct:createListOfGtfsFiles}, and the Camel
 * aggregator in front of them: many "export the merged GTFS" requests arrive close together and one export
 * serves them all, so the requests are batched instead of exported one by one.
 */
@Component
public class MergedGtfsExport {

    private static final Logger LOGGER = LoggerFactory.getLogger(MergedGtfsExport.class);

    /**
     * The batched-request kind. It is written into the {@code batched_request} table, so rows recorded by
     * one version are only picked up by the next if the string is unchanged.
     */
    static final String KIND = "gtfs-merged-export";

    /**
     * The only headers that travel to damu.
     *
     * <p>{@code HeaderPreservingGroupedMessageAggregationStrategy} kept exactly these five from the newest
     * request and dropped everything else, so this is the attribute set damu and the dispatcher topic see
     * today. The batch keeps whole messages, so passing one straight on would newly publish the admin
     * caller's username, or the triggering codespace's file handles, onto a topic other services read.
     */
    private static final List<String> HEADERS_TO_DAMU = List.of(
            DATASET_REFERENTIAL, CORRELATION_ID, PROVIDER_ID, CHOUETTE_REFERENTIAL, ET_CLIENT_NAME_HEADER);

    private final BatchRunner batchRunner;
    private final BatchedRequests requests;
    private final ProviderRepository providerRepository;
    private final MardukPubSubPublisher publisher;
    private final boolean scheduleEnabled;
    private final long inactivityTimeoutMillis;

    private int waitingAtLastCheck;
    private long lastChange = System.nanoTime();

    public MergedGtfsExport(
            BatchRunner batchRunner,
            BatchedRequests requests,
            ProviderRepository providerRepository,
            MardukPubSubPublisher publisher,
            @Value("${gtfs.export.autoStartup:true}") boolean scheduleEnabled,
            @Value("${gtfs.export.aggregation.timeout:300000}") long inactivityTimeoutMillis) {
        this.batchRunner = batchRunner;
        this.requests = requests;
        this.providerRepository = providerRepository;
        this.publisher = publisher;
        this.scheduleEnabled = scheduleEnabled;
        this.inactivityTimeoutMillis = inactivityTimeoutMillis;
    }

    /**
     * The timeout half of the aggregator's two completion triggers.
     *
     * <p>{@code gtfs.export.aggregation.timeout} is an inactivity timeout, as
     * {@code AggregateDefinition.completionTimeout} was: the batch is served once no new request has been
     * recorded for that long, so a burst of provider exports produces one merge rather than one merge per
     * export. Serving on a fixed period instead would split a burst that straddles a tick, and damu's
     * merge is the expensive end of that.
     *
     * <p>The quiet period is measured off the number of requests waiting rather than a local timestamp,
     * because a request recorded by the other replica has to reset it too - the aggregator only ever saw
     * requests on the pod holding the {@code master:} lock, and this consumer runs on both. The check
     * interval is the resolution of the measurement, and the cost of it is one indexed count.
     *
     * <p>{@code gtfs.export.autoStartup} gates this schedule and nothing else, as it does for the Chouette
     * job cleanup: it used to decide whether the consumer route started at all, which also disabled the
     * admin endpoint that asks for a merged export.
     */
    @Scheduled(fixedDelayString = "${gtfs.export.aggregation.check.interval:5000}")
    void serveTheBatchOnceRequestsStopArriving() {
        if (!scheduleEnabled) {
            LOGGER.debug("The scheduled merged GTFS export is switched off");
            return;
        }
        int waiting = requests.waiting(KIND);
        if (waiting != waitingAtLastCheck) {
            waitingAtLastCheck = waiting;
            lastChange = System.nanoTime();
            return;
        }
        if (waiting == 0 || quietFor() < inactivityTimeoutMillis) {
            return;
        }
        // Before serving, so a failed run waits out another quiet period instead of being retried every
        // check interval.
        lastChange = System.nanoTime();
        LOGGER.info("No new merged GTFS export request for {} ms, serving the {} waiting",
                inactivityTimeoutMillis, waiting);
        serveTheBatch();
    }

    private long quietFor() {
        return Duration.ofNanos(System.nanoTime() - lastChange).toMillis();
    }

    /**
     * Runs one merged export for everything waiting, or nothing if nothing is.
     *
     * <p>Synchronized because the size trigger calls this from a PubSub consumer thread while the schedule
     * calls it from the scheduler's. {@code gtfsExportExecutorService} had a pool size of 1 so that only
     * one GTFS export ran at a time; two concurrent runs would each publish a merge request to damu, which
     * is the duplicate work the batch exists to avoid.
     */
    synchronized void serveTheBatch() {
        batchRunner.run(KIND, this::export);
    }

    private void export(MardukMessage request) {
        String files = String.join(",", aggregatedGtfsFiles());
        LOGGER.info("Triggering merging and aggregation of GTFS files {} in damu", files);
        MardukMessage toDamu = new MardukMessage().setBody(files);
        HEADERS_TO_DAMU.forEach(header -> toDamu.setHeaderIfPresent(header, request.getHeader(header)));
        toDamu.setHeader(GTFS_ROUTE_DISPATCHER_HEADER_NAME, GTFS_ROUTE_DISPATCHER_AGGREGATION_HEADER_VALUE);
        publisher.publish(MardukQueues.GTFS_ROUTE_DISPATCHER_TOPIC, toDamu);
    }

    /** The per-provider exports damu merges: only the providers that keep their own data. */
    private List<String> aggregatedGtfsFiles() {
        return providerRepository.getProviders().stream()
                .filter(provider -> provider.getChouetteInfo().getMigrateDataToProvider() == null)
                .map(Provider::getChouetteInfo)
                .map(info -> info.getReferential() + "-" + CURRENT_AGGREGATED_GTFS_FILENAME)
                .toList();
    }
}

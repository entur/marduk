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

package no.rutebanken.marduk.routes.gtfs;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.aggregation.HeaderPreservingGroupedMessageAggregationStrategy;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

import static no.rutebanken.marduk.Constants.*;

/**
 * Route triggering a merge of GTFS files and upload of the resulting files to GCS.
 * <p>
 */
@Component
public class GtfsMergedExportRouteBuilder extends BaseRouteBuilder {

    private static final String STATUS_MERGE_OK = "ok";
    private static final String STATUS_MERGE_STARTED = "started";
    private static final String STATUS_MERGE_FAILED = "failed";
    private static final String STATUS_HEADER = "status";

    @Value("${gtfs.export.aggregation.timeout:300000}")
    private int gtfsExportAggregationTimeout;

    @Value("${aggregation.completionSize:100}")
    private int aggregationCompletionSize;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("google-pubsub:{{marduk.pubsub.project.id}}:GtfsExportMergedQueue").autoStartup("{{gtfs.export.autoStartup:true}}")
                .log(LoggingLevel.INFO, correlation() + "Starting GtfsExportMergedExportRouteBuilder")
                .process(this::removeSynchronizationForAggregatedExchange)
                .aggregate(simple("true", Boolean.class))
                .aggregationStrategy(
                        new HeaderPreservingGroupedMessageAggregationStrategy(
                                List.of(
                                    Constants.DATASET_REFERENTIAL,
                                    Constants.CORRELATION_ID,
                                    Constants.PROVIDER_ID,
                                    Constants.CHOUETTE_REFERENTIAL,
                                    Constants.ET_CLIENT_NAME_HEADER
                                )
                        )
                )
                .completionSize(aggregationCompletionSize)
                .completionTimeout(gtfsExportAggregationTimeout)
                .executorService("gtfsExportExecutorService")
                .process(this::addSynchronizationForAggregatedExchange)
                .log(LoggingLevel.INFO, correlation() + "Aggregated ${exchangeProperty.CamelAggregatedSize} GTFS export merged requests (aggregation completion triggered by ${exchangeProperty.CamelAggregatedCompletedBy}).")
                .log(LoggingLevel.INFO, correlation() + "Preparing GTFS export message from marduk to damu")
                .to("direct:exportMergedGtfs")
                .routeId("gtfs-extended-export-merged-route");

        from("direct:exportMergedGtfs")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Start export of merged GTFS file: ${header." + FILE_NAME + "}")
                .to("direct:createListOfGtfsFiles")
                .convertBodyTo(String.class, "UTF-8")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Triggering merging and aggregation of GTFS files ${body} in damu")
                .setHeader(GTFS_ROUTE_DISPATCHER_HEADER_NAME, simple(GTFS_ROUTE_DISPATCHER_AGGREGATION_HEADER_VALUE))
                .to("google-pubsub:{{marduk.pubsub.project.id}}:GtfsRouteDispatcherTopic")
                .id("damuAggregateGtfsNext")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Done sending message on pubsub")
                .routeId("gtfs-export-merged-route");

        from("direct:createListOfGtfsFiles")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Creating list of gtfs files for aggregation")
                .process(e -> e.getIn().setBody(String.join(",", getAggregatedGtfsFiles())))
                .routeId("gtfs-export-list-files-route");

        from("google-pubsub:{{marduk.pubsub.project.id}}:MardukAggregateGtfsStatusQueue")
                .choice()
                .when(header(STATUS_HEADER).isEqualTo(STATUS_MERGE_OK))
                .log(LoggingLevel.INFO, correlation() + "Received status OK from damu aggregation")
                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.TIMETABLE_PUBLISH).state(JobEvent.State.OK).correlationId(e.getIn().getHeader(CORRELATION_ID, String.class)).action(JobEvent.TimetableAction.EXPORT_GTFS_MERGED).build())
                .to(ExchangePattern.InOnly, "direct:updateStatus")
                .when(header(STATUS_HEADER).isEqualTo(STATUS_MERGE_STARTED))
                .log(LoggingLevel.INFO, correlation() + "Received status STARTED from damu aggregation")
                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.TIMETABLE_PUBLISH).state(JobEvent.State.STARTED).action(JobEvent.TimetableAction.EXPORT_GTFS_MERGED).newCorrelationId().build())
                .to(ExchangePattern.InOnly, "direct:updateStatus")
                .when(header(STATUS_HEADER).isEqualTo(STATUS_MERGE_FAILED))
                .log(LoggingLevel.INFO, correlation() + "Received status FAILED from damu aggregation")
                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.TIMETABLE_PUBLISH).state(JobEvent.State.FAILED).action(JobEvent.TimetableAction.EXPORT_GTFS_MERGED).correlationId(e.getIn().getHeader(CORRELATION_ID, String.class)).build())
                .to(ExchangePattern.InOnly, "direct:updateStatus")
                .end()
                .routeId("gtfs-aggregate-status-route");
    }

    private List<String> getAggregatedGtfsFiles() {
        return getProviderRepository().getProviders().stream()
                .filter(p -> p.getChouetteInfo().getMigrateDataToProvider() == null)
                .map(p -> p.getChouetteInfo().getReferential() + "-" + CURRENT_AGGREGATED_GTFS_FILENAME)
                .toList();
    }
}

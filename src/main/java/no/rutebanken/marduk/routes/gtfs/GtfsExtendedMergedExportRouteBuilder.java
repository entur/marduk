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
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.processor.aggregate.GroupedMessageAggregationStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Route creating a merged GTFS extended file for all providers and uploading it to GCS.
 * <p>
 * This file included fields and values proposed as GTFS extensions and as thus does not strictly adhere to the specification.
 *
 * @see no.rutebanken.marduk.routes.gtfs.GtfsBasicMergedExportRouteBuilder for strict GTFS export
 */
@Component
public class GtfsExtendedMergedExportRouteBuilder extends BaseRouteBuilder {


    @Value("${gtfs.norway.merged.file.name:rb_norway-aggregated-gtfs.zip}")
    private String gtfsNorwayMergedFileName;

    @Value("${gtfs.export.aggregation.timeout:300000}")
    private int gtfsExportAggregationTimeout;

    @Value("${aggregation.completionSize:100}")
    private int aggregationCompletionSize;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("google-pubsub:{{marduk.pubsub.project.id}}:GtfsExportMergedQueue").autoStartup("{{gtfs.export.autoStartup:true}}")
                .log(LoggingLevel.INFO, "Starting GtfsExportMergedExportRouteBuilder")
                .process(this::removeSynchronizationForAggregatedExchange)
                .aggregate(simple("true", Boolean.class))
                .aggregationStrategy(new GroupedMessageAggregationStrategy())
                .completionSize(aggregationCompletionSize)
                .completionTimeout(gtfsExportAggregationTimeout)
                .executorService("gtfsExportExecutorService")
                .process(this::addSynchronizationForAggregatedExchange)
                .process(this::setNewCorrelationId)
                .log(LoggingLevel.INFO, correlation() +  "Aggregated ${exchangeProperty.CamelAggregatedSize} GTFS export merged requests (aggregation completion triggered by ${exchangeProperty.CamelAggregatedCompletedBy}).")
                .choice()
                    .when(simple("${properties:marduk.gtfs-aggregation-next.enabled} == 'true'"))
                        .to("direct:exportGtfsExtendedMergedNext")
                    .otherwise()
                        .to("direct:exportGtfsExtendedMerged")
                .end()
                .to(ExchangePattern.InOnly, "google-pubsub:{{marduk.pubsub.project.id}}:GtfsBasicExportMergedQueue")
                .routeId("gtfs-extended-export-merged-route");

        from("direct:exportGtfsExtendedMergedNext")
                .log(LoggingLevel.INFO, "Preparing GTFS extended export message from marduk to damu")
                .setBody(constant(""))
                .setHeader(Constants.FILE_NAME, constant(gtfsNorwayMergedFileName))
                .setHeader(Constants.JOB_ACTION, constant(JobEvent.TimetableAction.EXPORT_GTFS_MERGED.name()))
                .to("direct:exportMergedGtfsNext")
                .routeId("gtfs-extended-export-merged-next");

        from("direct:exportGtfsExtendedMerged")
                .setBody(constant(""))
                .setHeader(Constants.FILE_NAME, constant(gtfsNorwayMergedFileName))
                .setHeader(Constants.JOB_ACTION, constant(JobEvent.TimetableAction.EXPORT_GTFS_MERGED.name()))
                .to("direct:exportMergedGtfs")
                .routeId("gtfs-extended-export-merged");
    }
}

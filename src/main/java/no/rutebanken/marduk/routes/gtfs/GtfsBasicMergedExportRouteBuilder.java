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
import org.apache.camel.LoggingLevel;
import org.apache.camel.processor.aggregate.GroupedMessageAggregationStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Route creating a merged GTFS basic file for all providers and uploading it to GCS.
 * <p>
 * This file is adhering strictly to the GTFS specification, excluding all extended gtfs types.
 *
 * @see no.rutebanken.marduk.routes.gtfs.GtfsBasicMergedExportRouteBuilder for extended GTFS export
 */
@Component
public class GtfsBasicMergedExportRouteBuilder extends BaseRouteBuilder {

    @Value("${gtfs.basic.export.merged.file.name:rb_norway-aggregated-gtfs-basic.zip}")
    private String gtfsBasicMergedFileName;

    @Value("${gtfs.basic.norway.includes.shapes:false}")
    private boolean includeShapes;

    @Value("${gtfs.export.aggregation.timeout:300000}")
    private int gtfsExportAggregationTimeout;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("google-pubsub:{{marduk.pubsub.project.id}}:GtfsBasicExportMergedQueue")
                .autoStartup("{{gtfs.export.basic.autoStartup:true}}")
                .process(this::removeSynchronizationForAggregatedExchange)
                .aggregate(simple("true", Boolean.class))
                .aggregationStrategy(new GroupedMessageAggregationStrategy())
                .completionSize(100)
                .completionTimeout(gtfsExportAggregationTimeout)
                .executorService("gtfsExportExecutorService")
                .process(this::addSynchronizationForAggregatedExchange)
                .process(this::setNewCorrelationId)
                .log(LoggingLevel.INFO, correlation() + "Aggregated ${exchangeProperty.CamelAggregatedSize} GTFS Basics export requests (aggregation completion triggered by ${exchangeProperty.CamelAggregatedCompletedBy}).")
                .to("direct:exportGtfsBasicMerged")
                .routeId("gtfs-basic-export-merged-route");

        from("direct:exportGtfsBasicMerged")
                .setBody(constant(""))
                .setHeader(Constants.FILE_NAME, constant(gtfsBasicMergedFileName))
                .setHeader(Constants.INCLUDE_SHAPES, constant(includeShapes))
                .to("direct:exportMergedGtfs")
                .routeId("gtfs-basic-export-merged");
    }
}

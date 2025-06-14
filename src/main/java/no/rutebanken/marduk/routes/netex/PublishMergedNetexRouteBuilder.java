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

package no.rutebanken.marduk.routes.netex;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.*;

/**
 * Publish dated merged NeTEx dataset and notify downstream consumers (GTFS export, OTP Graph builder, Kafka topic)
 */
@Component
public class PublishMergedNetexRouteBuilder extends BaseRouteBuilder {

    @Value("${gtfs.export.chouette:true}")
    private boolean useChouetteGtfsExport;

    @Value("${line.statistics.calculation.enabled:false}")
    private boolean lineStatisticsCalculationEnabled;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("google-pubsub:{{marduk.pubsub.project.id}}:PublishMergedNetexQueue")
                .to("direct:publishMergedDataset")
                .routeId("netex-publish-merged-netex-queue");


        from("direct:publishMergedDataset")
                .filter(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).getChouetteInfo().isGenerateDatedServiceJourneyIds())
                .to("direct:copyDatedExport")
                .end()

                .wireTap("direct:notifyExportNetexWithFlexibleLines")
                .setBody(constant(""))
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.BUILD_GRAPH).state(JobEvent.State.PENDING).build())
                .to("direct:updateStatus")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "FlexibleLines merging OK, triggering OTP graph build.")
                .to("google-pubsub:{{marduk.pubsub.project.id}}:Otp2GraphBuildQueue")
                .to("direct:startDamuGtfsExport")
                .filter(constant(lineStatisticsCalculationEnabled))
                    .to("google-pubsub:{{marduk.pubsub.project.id}}:LineStatisticsCalculationQueue")
                .end()
                .routeId("publish-merged-dataset");

        from("direct:notifyExportNetexWithFlexibleLines")
                .setBody(header(CHOUETTE_REFERENTIAL).regexReplaceAll("rb_", ""))
                .removeHeaders("*")
                .to("google-pubsub:{{marduk.pubsub.project.id}}:NetexExportNotificationQueue")
                .routeId("netex-notify-export");

        from("direct:startDamuGtfsExport")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Triggering GTFS export in Damu.")
                .filter(PredicateBuilder.not(constant(useChouetteGtfsExport)))
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT).state(JobEvent.State.PENDING).build())
                //end filter
                .end()
                .removeHeader(DATASET_REFERENTIAL)
                .setBody(header(CHOUETTE_REFERENTIAL))
                .process(this::removeAllCamelHeaders)
                .setHeader(GTFS_ROUTE_DISPATCHER_HEADER_NAME, simple(GTFS_ROUTE_DISPATCHER_EXPORT_HEADER_VALUE))
                .to("google-pubsub:{{marduk.pubsub.project.id}}:GtfsRouteDispatcherTopic")
                .routeId("start-damu-gtfs-export");
    }
}

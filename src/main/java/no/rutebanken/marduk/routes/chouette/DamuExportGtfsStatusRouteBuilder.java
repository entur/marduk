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

package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;

@Component
public class DamuExportGtfsStatusRouteBuilder extends AbstractChouetteRouteBuilder {

    private static final String STATUS_EXPORT_STARTED = "started";
    private static final String STATUS_EXPORT_OK = "ok";
    private static final String STATUS_EXPORT_FAILED = "failed";

    @Value("${gtfs.export.chouette:true}")
    private boolean useChouetteGtfsExport;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("entur-google-pubsub:DamuExportGtfsStatusQueue")
                .process(this::setCorrelationIdIfMissing)
                .choice()
                .when(body().isEqualTo(constant(STATUS_EXPORT_STARTED)))
                .to("direct:damuGtfsExportStarted")
                .when(body().isEqualTo(constant(STATUS_EXPORT_OK)))
                .to("direct:damuGtfsExportComplete")
                .when(body().isEqualTo(constant(STATUS_EXPORT_FAILED)))
                .to("direct:damuGtfsExportFailed")
                .routeId("damu-status-export-gtfs");

        from("direct:damuGtfsExportStarted")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Damu GTFS export started for codespace ${header." + DATASET_REFERENTIAL + "}")
                .filter(PredicateBuilder.not(constant(useChouetteGtfsExport)))
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT).state(JobEvent.State.STARTED).build())
                .to("direct:updateStatus")
                .routeId("damu-started-export-gtfs");

        from("direct:damuGtfsExportComplete")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Damu GTFS export complete for codespace ${header." + DATASET_REFERENTIAL + "}")
                .filter(PredicateBuilder.not(constant(useChouetteGtfsExport)))
                .to("entur-google-pubsub:GtfsExportMergedQueue")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT).state(JobEvent.State.OK).build())
                .to("direct:updateStatus")
                .routeId("damu-complete-export-gtfs");

        from("direct:damuGtfsExportFailed")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Damu GTFS export failed for codespace ${header." + DATASET_REFERENTIAL + "}")
                .filter(PredicateBuilder.not(constant(useChouetteGtfsExport)))
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT).state(JobEvent.State.FAILED).build())
                .to("direct:updateStatus")
                .routeId("damu-failed-export-gtfs");


    }


}

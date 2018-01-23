package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.http4.HttpMethods;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Route deleting old chouette jobs at regular intervals.
 */

@Component
public class ChouetteRemoveOldJobsRouteBuilder extends BaseRouteBuilder {
    @Value("${chouette.remove.old.jobs.cron.schedule:0+15+23+?+*+MON-FRI}")
    private String cronSchedule;

    @Value("${chouette.remove.old.jobs.keep.days:100}")
    private int keepDays;

    @Value("${chouette.remove.old.jobs.keep.jobs:100}")
    private int keepJobs;

    @Value("${chouette.url}")
    private String chouetteUrl;


    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("quartz2://marduk/chouetteRemoveOldJobsQuartz?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{chouette.remove.old.jobs.autoStartup:true}}")
                .filter(e -> isSingletonRouteActive(e.getFromRouteId()))
                .log(LoggingLevel.INFO, "Quartz triggers deletion of old jobs in Chouette.")
                .to("direct:chouetteRemoveOldJobs")
                .routeId("chouette-remove-old-jobs-quartz");


        from("direct:chouetteRemoveOldJobs")
                .log(LoggingLevel.INFO, correlation() + "Starting Chouette remove old jobs")
                .removeHeaders("Camel*")
                .setBody(constant(null))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.DELETE))

                .choice().when(header("keepJobs").isNull())
                .setHeader("keepJobs", constant(keepJobs))
                .end()

                .choice().when(header("keepDays").isNull())
                .setHeader("keepDays", constant(keepDays))
                .end()

                .toD(chouetteUrl + "/chouette_iev/admin/completed_jobs?keepJobs=${header.keepJobs}&keepDays=${header.keepDays}")
                .log(LoggingLevel.INFO, correlation() + "Completed Chouette remove old jobs")
                .routeId("chouette-remove-old-jobs");

    }
}

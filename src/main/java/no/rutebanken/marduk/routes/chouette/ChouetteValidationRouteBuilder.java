package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.chouette.json.Parameters;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEvent.TimetableAction;
import no.rutebanken.marduk.routes.status.JobEvent.State;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.Utils.getLastPathElementOfUrl;

/**
 * Runs validation in Chouette
 */
@Component
public class ChouetteValidationRouteBuilder extends AbstractChouetteRouteBuilder {
    @Value("${chouette.validate.level1.cron.schedule:0+0+23+?+*+MON-FRI}")
    private String cronSchedule;
    @Value("${chouette.url}")
    private String chouetteUrl;

    @Override
    public void configure() throws Exception {
        super.configure();


        singletonFrom("quartz2://marduk/chouetteValidateLevel1?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{chouette.validate.level1.autoStartup:true}}")
                .filter(e -> isLeader(e.getFromRouteId()))
                .log(LoggingLevel.INFO, "Quartz triggers validation of Level1 for all providers in Chouette.")
                .to("direct:chouetteValidateLevel1ForAllProviders")
                .routeId("chouette-validate-level1-quartz");


        from("direct:chouetteValidateLevel1ForAllProviders")
                .process(e -> e.getIn().setBody(getProviderRepository().getProviders()))
                .split().body().parallelProcessing().executorService(allProvidersExecutorService)
                .filter(simple("${body.chouetteInfo.migrateDataToProvider}"))
                .process(e -> e.getIn().setHeader(CORRELATION_ID, UUID.randomUUID().toString()))
                .setHeader(PROVIDER_ID, simple("${body.id}"))
                .setHeader(CHOUETTE_REFERENTIAL, simple("${body.chouetteInfo.referential}"))
                .setHeader(CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL, constant(JobEvent.TimetableAction.VALIDATION_LEVEL_1.name()))
                .setBody(constant(null))
                .inOnly("activemq:queue:ChouetteValidationQueue")
                .routeId("chouette-validate-level1-all-providers");


        from("activemq:queue:ChouetteValidationQueue?transacted=true&maxConcurrentConsumers=3").streamCaching()
                .transacted()
                .log(LoggingLevel.INFO, correlation() + "Starting Chouette validation")
                .process(e -> {
                    // Add correlation id only if missing
                    e.getIn().setHeader(Constants.CORRELATION_ID, e.getIn().getHeader(Constants.CORRELATION_ID, UUID.randomUUID().toString()));
                    e.getIn().removeHeader(Constants.CHOUETTE_JOB_ID);
                })
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(e.getIn().getHeader(CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL, JobEvent.TimetableAction.class)).state(State.PENDING).build())
                .to("direct:updateStatus")

                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .process(e -> e.getIn().setHeader(JSON_PART, Parameters.getValidationParameters(getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class))))) //Using header to addToExchange json data
                .log(LoggingLevel.DEBUG, correlation() + "Creating multipart request")
                .process(e -> toGenericChouetteMultipart(e))
                .setHeader(Exchange.CONTENT_TYPE, simple("multipart/form-data"))
                .toD(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/validator") // TODO set to validation
                .process(e -> {
                    e.getIn().setHeader(Constants.CHOUETTE_JOB_STATUS_URL, e.getIn().getHeader("Location").toString().replaceFirst("http", "http4"));
                    e.getIn().setHeader(Constants.CHOUETTE_JOB_ID, getLastPathElementOfUrl(e.getIn().getHeader("Location", String.class)));
                })
                .setHeader(Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION, constant("direct:processValidationResult"))
                .process(e -> {
                    e.getIn().setHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE, e.getIn().getHeader(Constants.CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL));
                })
                .removeHeader("loopCounter")
                .to("activemq:queue:ChouettePollStatusQueue")
                .routeId("chouette-send-validation-job");

        // Will be sent here after polling completes
        from("direct:processValidationResult")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .setBody(constant(""))
                .choice()
                .when(simple("${header.action_report_result} == 'OK' and ${header.validation_report_result} == 'OK'"))
                .to("direct:checkScheduledJobsBeforeTriggeringExport")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(e.getIn().getHeader(CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL, TimetableAction.class)).state(State.OK).build())
                .when(simple("${header.action_report_result} == 'OK' and ${header.validation_report_result} == 'NOK'"))
                .log(LoggingLevel.INFO, correlation() + "Validation failed (processed ok, but timetable data is faulty)")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(e.getIn().getHeader(CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL, TimetableAction.class)).state(State.FAILED).build())
                .otherwise()
                .log(LoggingLevel.ERROR, correlation() + "Validation went wrong")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(e.getIn().getHeader(CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL, TimetableAction.class)).state(State.FAILED).build())
                .end()
                .to("direct:updateStatus")
                .routeId("chouette-process-validation-status");

        // Check that no other import jobs in status SCHEDULED exists for this referential. If so, do not trigger export
        from("direct:checkScheduledJobsBeforeTriggeringExport")
                .setProperty("job_status_url", simple("{{chouette.url}}/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/jobs?timetableAction=importer&status=SCHEDULED&status=STARTED"))
                .toD("${exchangeProperty.job_status_url}")
                .choice()
                .when().jsonpath("$.*[?(@.status == 'SCHEDULED')].status")
                .log(LoggingLevel.INFO, correlation() + "Validation ok, skipping export as there are more import jobs active")
                .when(method(getClass(), "shouldTransferData").isEqualTo(true))
                .log(LoggingLevel.INFO, correlation() + "Validation ok, transfering data to next dataspace")
                .to("activemq:queue:ChouetteTransferExportQueue")
                .otherwise()
                .log(LoggingLevel.INFO, correlation() + "Validation ok, triggering GTFS export.")
                .setBody(constant(""))
                .to("activemq:queue:ChouetteExportQueue") // Check on provider if should trigger transfer
                .end()
                .routeId("chouette-process-job-list-after-validation");

    }


}



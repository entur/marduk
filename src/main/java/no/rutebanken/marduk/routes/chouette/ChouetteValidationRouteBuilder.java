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
    @Value("${chouette.validate.level1.cron.schedule:0+30+23+?+*+MON-FRI}")
    private String level1CronSchedule;

    @Value("${chouette.validate.level2.cron.schedule:0+30+21+?+*+MON-FRI}")
    private String level2CronSchedule;
    @Value("${chouette.url}")
    private String chouetteUrl;

    @Override
    public void configure() throws Exception {
        super.configure();


        singletonFrom("quartz2://marduk/chouetteValidateLevel1?cron=" + level1CronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{chouette.validate.level1.autoStartup:true}}")
                .transacted()
                .filter(e -> shouldQuartzRouteTrigger(e, level1CronSchedule))
                .log(LoggingLevel.INFO, "Quartz triggers validation of Level1 for all providers in Chouette.")
                .inOnly("direct:chouetteValidateLevel1ForAllProviders")
                .routeId("chouette-validate-level1-quartz");

        singletonFrom("quartz2://marduk/chouetteValidateLevel2?cron=" + level2CronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{chouette.validate.level2.autoStartup:false}}")
                .transacted()
                .filter(e -> shouldQuartzRouteTrigger(e, level2CronSchedule))
                .log(LoggingLevel.INFO, "Quartz triggers validation of Level2 for all providers in Chouette.")
                .inOnly("direct:chouetteValidateLevel2ForAllProviders")
                .routeId("chouette-validate-level2-quartz");

        // Trigger validation level1 for all level1 providers (ie migrateDateToProvider and referential set)
        from("direct:chouetteValidateLevel1ForAllProviders")
                .process(e -> e.getIn().setBody(getProviderRepository().getProviders()))
                .split().body().parallelProcessing().executorService(allProvidersExecutorService)
                .filter(simple("${body.chouetteInfo.migrateDataToProvider} && ${body.chouetteInfo.referential}"))
                .process(e -> e.getIn().setHeader(CORRELATION_ID, UUID.randomUUID().toString()))
                .setHeader(PROVIDER_ID, simple("${body.id}"))
                .setHeader(CHOUETTE_REFERENTIAL, simple("${body.chouetteInfo.referential}"))
                .setHeader(CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL, constant(JobEvent.TimetableAction.VALIDATION_LEVEL_1.name()))
                .setBody(constant(null))
                .inOnly("activemq:queue:ChouetteValidationQueue")
                .routeId("chouette-validate-level1-all-providers");


        // Trigger validation level2 for all level2 providers (ie no migrateDateToProvider and referential set)
        from("direct:chouetteValidateLevel2ForAllProviders")
                .process(e -> e.getIn().setBody(getProviderRepository().getProviders()))
                .split().body().parallelProcessing().executorService(allProvidersExecutorService)
                .filter(simple("${body.chouetteInfo.migrateDataToProvider} == null && ${body.chouetteInfo.referential}"))
                .process(e -> e.getIn().setHeader(CORRELATION_ID, UUID.randomUUID().toString()))
                .setHeader(PROVIDER_ID, simple("${body.id}"))
                .setHeader(CHOUETTE_REFERENTIAL, simple("${body.chouetteInfo.referential}"))
                .setHeader(CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL, constant(JobEvent.TimetableAction.VALIDATION_LEVEL_2.name()))
                .setBody(constant(null))
                .inOnly("activemq:queue:ChouetteValidationQueue")
                .routeId("chouette-validate-level2-all-providers");


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

                .to("direct:assertHeadersForChouetteValidation")

                .log(LoggingLevel.DEBUG, correlation() + "Creating multipart request")
                .process(e -> toGenericChouetteMultipart(e))
                .setHeader(Exchange.CONTENT_TYPE, simple("multipart/form-data"))
                .toD(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/validator")
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

        from("direct:assertHeadersForChouetteValidation")
                .choice()
                .when(simple("${header." + CHOUETTE_REFERENTIAL + "} == null or ${header." + PROVIDER_ID + "} == null "))
                .log(LoggingLevel.WARN, correlation() + "Unable to start Chouette validation for missing referential or providerId")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(e.getIn().getHeader(CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL, JobEvent.TimetableAction.class)).state(State.FAILED).build())
                .to("direct:updateStatus")
                .stop()
                .end()
                .routeId("chouette-send-validation-job-validate-headers");

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
                .to("activemq:queue:ChouetteExportNetexQueue") // Check on provider if should trigger transfer
                .end()
                .routeId("chouette-process-job-list-after-validation");

    }

}



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

import com.fasterxml.jackson.databind.JsonMappingException;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.chouette.json.ActionReportWrapper;
import no.rutebanken.marduk.routes.chouette.json.JobResponse;
import no.rutebanken.marduk.routes.chouette.json.JobResponseWithLinks;
import no.rutebanken.marduk.routes.chouette.mapping.ProviderAndJobsMapper;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEvent.State;
import no.rutebanken.marduk.routes.status.JobEvent.TimetableAction;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.component.google.pubsub.GooglePubsubConstants;
import org.apache.camel.component.http.HttpMethods;
import org.apache.camel.component.jackson.ListJacksonDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.hc.core5.net.URIBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.routes.chouette.json.Status.ABORTED;
import static no.rutebanken.marduk.routes.chouette.json.Status.CANCELED;
import static no.rutebanken.marduk.routes.chouette.json.Status.RESCHEDULED;
import static no.rutebanken.marduk.routes.chouette.json.Status.SCHEDULED;
import static no.rutebanken.marduk.routes.chouette.json.Status.STARTED;

@Component
public class ChouettePollJobStatusRoute extends AbstractChouetteRouteBuilder {


    private static final String PUBSUB_MESSAGE_ID = "PUBSUB_MESSAGE_ID";

    @Value("${chouette.max.retries:3000}")
    private int maxRetries;

    @Value("${chouette.retry.delay:15000}")
    private long retryDelay;

    @Value("${chouette.url}")
    private String chouetteUrl;

    /**
     * This routebuilder polls a job until it is terminated. It expects a few headers set on the message it receives:
     * Constants.CHOUETTE_JOB_STATUS_URL - the url to poll
     * Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION - a routing slip which defines where to send poll result
     * .. and a few more related to status updates
     */

    @SuppressWarnings("unchecked")
    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:chouetteGetJobsForProvider")
                .log(LoggingLevel.DEBUG, correlation() + "Fetching jobs for provider id '${header." + PROVIDER_ID + "}'")
                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).getChouetteInfo().getReferential()))
                .setProperty("chouette_url", simple(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/jobs"))
                .to("direct:chouetteGetJobs")
                .routeId("chouette-list-jobs-for-provider");

        from("direct:chouetteGetJobs")
                .process(this::removeAllCamelHeaders)
                .setBody(constant(""))
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http.HttpMethods.GET))
                .process(e -> {
                    String url = (String) e.getProperty("chouette_url");

                    // Convert camel dynamic endpoint format (http:xxxx) to url (http://xxx) before manipulating url. Needed as interception
                    // does not seem to work with // in target anymore (as of camel 2.22.0)
                    boolean dynamicEndpointNotation=!url.contains("://");
                    if (dynamicEndpointNotation) {
                        url = url.replaceFirst(":", "://");
                    }
                    URIBuilder b = new URIBuilder(url);
                    if (e.getIn().getHeader("action") != null) {
                        b.addParameter("action", (String) e.getIn().getHeader("action"));
                    }
                    if (e.getIn().getHeader("status") != null) {

                        Object status = e.getIn().getHeader("status");
                        if (status instanceof List) {
                            for (String s : (List<String>) status) {
                                b.addParameter("status", s);
                            }
                        } else {
                            b.addParameter("status", (String) status);
                        }
                    }
                    b.addParameter("addActionParameters", Boolean.FALSE.toString());
                    e.setProperty("chouette_url", b.toString());
                })
                .toD("${exchangeProperty.chouette_url}")
                .unmarshal(new ListJacksonDataFormat(JobResponse.class))
                .routeId("chouette-list-jobs");


        from("direct:chouetteCancelJob")
                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).getChouetteInfo().getReferential()))
                .process(this::removeAllCamelHeaders)
                .setBody(constant(""))
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http.HttpMethods.DELETE))
                .setProperty("chouette_url", simple(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/scheduled_jobs/${header." + Constants.CHOUETTE_JOB_ID + "}"))
                .toD("${exchangeProperty.chouette_url}")
                .setBody(constant(""))
                .routeId("chouette-cancel-job");

        from("direct:chouetteCancelAllJobsForProvider")
                .process(e -> e.getIn().setHeader("status", Arrays.asList("STARTED", "SCHEDULED")))
                .to("direct:chouetteGetJobsForProvider")
                .sort(body(), new JobResponseDescendingSorter())
                .process(this::removeAllCamelHeaders)
                .split().body().parallelProcessing().executorService("allProvidersExecutorService")
                .setHeader(Constants.CHOUETTE_JOB_ID, simple("${body.id}"))
                .setBody(constant(""))
                .to("direct:chouetteCancelJob")
                .routeId("chouette-cancel-all-jobs-for-provider");

        from("direct:chouetteCancelAllJobsForAllProviders")
                .process(e -> e.getIn().setBody(getProviderRepository().getProviders()))
                .split().body().parallelProcessing().executorService("allProvidersExecutorService")
                .setHeader(Constants.PROVIDER_ID, simple("${body.id}"))
                .setBody(constant(""))
                .process(this::removeAllCamelHeaders)
                .to("direct:chouetteCancelAllJobsForProvider")
                .routeId("chouette-cancel-all-jobs-for-all-providers");

        from("direct:chouetteGetJobsAll")
                .log(LoggingLevel.DEBUG, correlation() + "Fetching jobs for all providers}'")
                .setProperty("chouette_url", simple(chouetteUrl + "/chouette_iev/referentials/jobs"))
                .to("direct:chouetteGetJobs")
                .process(e -> {
                    JobResponse[] jobs = e.getIn().getBody(JobResponse[].class);
                    e.getIn().setBody(new ProviderAndJobsMapper().mapJobResponsesToProviderAndJobs(jobs, getProviderRepository().getProviders()));
                })
                .end()
                .routeId("chouette-get-jobs-all");


        from("google-pubsub:{{marduk.pubsub.project.id}}:ChouettePollStatusQueue")
                .validate(header(Constants.CORRELATION_ID).isNotNull())
                .validate(header(Constants.PROVIDER_ID).isNotNull())
                .validate(header(Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION).isNotNull())
                .validate(header(Constants.CHOUETTE_JOB_STATUS_URL).isNotNull())
                .validate(header(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE).isNotNull())
                .to("direct:checkJobStatus")
                .routeId("chouette-validate-job-status-parameters");

        from("direct:checkJobStatus")
                .process(e ->
                    e.getIn().setHeader("loopCounter", e.getIn().getHeader("loopCounter", 0, Integer.class) + 1)
                )
                .setHeader(PUBSUB_MESSAGE_ID, header(GooglePubsubConstants.MESSAGE_ID))
                .log(LoggingLevel.DEBUG, correlation() + "Checking status for job ${header."+ Constants.CHOUETTE_JOB_ID + "}. Polling counter: ${header.loopCounter} [PubSub message id: ${header." + PUBSUB_MESSAGE_ID + "}]")
                .setProperty(Constants.CHOUETTE_REFERENTIAL, header(Constants.CHOUETTE_REFERENTIAL))
                .setProperty("url", header(Constants.CHOUETTE_JOB_STATUS_URL))
                .process(this::removeAllCamelHeaders)
                .setBody(constant(""))
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http.HttpMethods.GET))
                .log(LoggingLevel.DEBUG, correlation() + "Calling chouette status url ${exchangeProperty.url} for job ${header."+ Constants.CHOUETTE_JOB_ID + "}. Polling counter: ${header.loopCounter} [PubSub message id: ${header." + PUBSUB_MESSAGE_ID + "}]")
                .toD("${exchangeProperty.url}")
                .log(LoggingLevel.DEBUG, correlation() + "Called chouette status url ${exchangeProperty.url} for job ${header."+ Constants.CHOUETTE_JOB_ID + "}. Polling counter: ${header.loopCounter} [PubSub message id: ${header." + PUBSUB_MESSAGE_ID + "}]")
                .unmarshal().json(JsonLibrary.Jackson, JobResponseWithLinks.class)
                .setProperty("current_status", simple("${body.status}"))
                .choice()
                .when(PredicateBuilder.or(simple("${body.status} != ${type:no.rutebanken.marduk.routes.chouette.json.Status.SCHEDULED} && ${body.status} != ${type:no.rutebanken.marduk.routes.chouette.json.Status.STARTED} && ${body.status} != ${type:no.rutebanken.marduk.routes.chouette.json.Status.RESCHEDULED}"),
                        simple("${header.loopCounter} > " + maxRetries)))
                .to("direct:jobStatusDone")
                .otherwise()
                // Update status
                .to("direct:rescheduleJob")

                .end()
                .routeId("chouette-get-job-status");


        from("direct:rescheduleJob")
                .log(LoggingLevel.DEBUG, correlation() + "Rescheduling job ${header."+ Constants.CHOUETTE_JOB_ID + "}. Polling counter: ${header.loopCounter} [PubSub message id: ${header." + PUBSUB_MESSAGE_ID + "}]")
                .filter(simple("${exchangeProperty.current_status} == '" + STARTED + "' && ${header.loopCounter} == 1"))
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.valueOf((String) e.getIn().getHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE))).state(State.STARTED).jobId(e.getIn().getHeader(Constants.CHOUETTE_JOB_ID, String.class)).build())
                .to("direct:updateStatus")
                .end()
                .setBody(constant(""))
                // sending a new message to ChouettePollStatusQueue is delayed and processed asynchronously in another thread (asyncDelayed = true by default).
                // Meanwhile the route is not blocked and can process other messages.
                .delay(retryDelay)
                .setBody(constant(""))
                .log(LoggingLevel.DEBUG, correlation() + "Resuming rescheduling job ${header."+ Constants.CHOUETTE_JOB_ID + "}. Polling counter: ${header.loopCounter} [PubSub message id: ${header." + PUBSUB_MESSAGE_ID + "}]")
                .to("google-pubsub:{{marduk.pubsub.project.id}}:ChouettePollStatusQueue")
                .log(LoggingLevel.DEBUG, correlation() + "Rescheduled job ${header."+ Constants.CHOUETTE_JOB_ID + "}. Polling counter: ${header.loopCounter} [PubSub message id: ${header." + PUBSUB_MESSAGE_ID + "}]")
                .routeId("chouette-reschedule-job");

        from("direct:jobStatusDone")
                .log(LoggingLevel.DEBUG, correlation() + "Exited retry loop with status ${header.current_status} for job ${header."+ Constants.CHOUETTE_JOB_ID + "} [PubSub message id: ${header." + PUBSUB_MESSAGE_ID + "}]")
                .to(logDebugShowAll())
                .choice()
                .when(simple("${header.current_status} == '" + SCHEDULED + "' || ${header.current_status} == '" + STARTED + "' || ${header.current_status} == '" + RESCHEDULED + "'"))
                .log(LoggingLevel.WARN, correlation() + "Job timed out with state ${header.current_status}. Config should probably be tweaked. Stopping route.")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.valueOf((String) e.getIn().getHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE))).state(State.TIMEOUT).build())
                .to("direct:updateStatus")
                .stop()
                .when(simple("${header.current_status} == '" + ABORTED + "'"))
                .log(LoggingLevel.WARN, correlation() + "Job ended in state FAILED. Stopping route.")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.valueOf((String) e.getIn().getHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE))).state(State.FAILED).build())
                .to("direct:updateStatus")
                .stop()
                .when(simple("${header.current_status} == '" + CANCELED + "'"))
                .log(LoggingLevel.WARN, correlation() + "Job ended in state CANCELLED. Stopping route.")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.valueOf((String) e.getIn().getHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE))).state(State.CANCELLED).build())
                .to("direct:updateStatus")
                .stop()
                .end()
                .process(e -> {
                    JobResponseWithLinks response = e.getIn().getBody(JobResponseWithLinks.class);
                    Optional<String> actionReportUrlOptional = response.getLinks().stream().filter(li -> "action_report".equals(li.getRel())).findFirst().map(li -> li.getHref());
                    e.getIn().setHeader("action_report_url", actionReportUrlOptional.orElseThrow(() -> new IllegalArgumentException("No URL found for action report.")));
                    Optional<String> validationReportUrlOptional = response.getLinks().stream().filter(li -> "validation_report".equals(li.getRel())).findFirst().map(li -> li.getHref());
                    e.getIn().setHeader("validation_report_url", validationReportUrlOptional.orElse(null));
                    Optional<String> dataUrlOptional = response.getLinks().stream().filter(li -> "data".equals(li.getRel())).findFirst().map(li -> li.getHref());
                    e.getIn().setHeader("data_url", dataUrlOptional.orElse(null));
                })
                // Fetch and parse action report
                .to(logDebugShowAll())
                .log(LoggingLevel.DEBUG, getClass().getName(), correlation() + "Calling action report url ${header.action_report_url}")
                .process(this::removeAllCamelHeaders)
                .setBody(simple(""))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .toD("${header.action_report_url}")
                .to(logDebugShowAll())

                .doTry()
                .unmarshal().json(JsonLibrary.Jackson, ActionReportWrapper.class).process(e -> { /* Dummy line to make doCatch available */})
                .doCatch(JsonMappingException.class)
                .log(LoggingLevel.WARN, correlation() + "Received invalid (empty?) action report for terminated job. Giving up.")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.valueOf((String) e.getIn().getHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE))).state(State.FAILED).build())
                .to("direct:updateStatus")
                .stop()
                .end()

                .choice().when(simple("${body.finalised} == false"))
                .choice().when(simple("${header.loopCounter} > " + maxRetries))
                .log(LoggingLevel.WARN, correlation() + "Received non-finalised action report for terminated job. Giving up.")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.valueOf((String) e.getIn().getHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE))).state(State.FAILED).build())
                .to("direct:updateStatus")
                .otherwise()
                .log(LoggingLevel.INFO, correlation() + "Received non-finalised action report for terminated job. Waiting before retry ")
                // Update status
                .to("direct:rescheduleJob")
                .end()
                .stop()
                .end()

                .process(e -> {
                            ActionReportWrapper.ActionReport actionReport = e.getIn().getBody(ActionReportWrapper.class).getActionReport();
                            e.getIn().setHeader("action_report_result", actionReport.getResult());
                            ActionReportWrapper.Failure failure = actionReport.getFailure();
                            if (failure != null && JobEvent.CHOUETTE_JOB_FAILURE_CODE_NO_DATA_FOUND.equals(failure.getCode())) {
                                e.getIn().setHeader(Constants.JOB_ERROR_CODE, JobEvent.JOB_ERROR_VALIDATION_NO_DATA);
                            }
                        }
                )

                // Fetch and parse validation report
                .to(logDebugShowAll())
                .choice()
                .when(simple("${header.validation_report_url} != null"))
                .log(LoggingLevel.DEBUG, correlation() + "Calling validation report url ${header.validation_report_url}")
                .process(this::removeAllCamelHeaders)
                .setBody(simple(""))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .toD("${header.validation_report_url}")
                .log(LoggingLevel.DEBUG, correlation() + "Called validation report url ${header.validation_report_url}")
                .to("direct:checkValidationReport")
                .otherwise()
                .setHeader("validation_report_result", constant("NOT_PRESENT"))
                .toD("${header." + Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION + "}")
                .end()
                .routeId("chouette-process-job-reports");

        from("direct:checkValidationReport")
                .choice()
                .when().jsonpath("$.validation_report.check_points[?(@.severity == 'ERROR' && @.result == 'NOK')]")
                .setHeader("validation_report_result", constant("NOK"))
                .otherwise()
                .setHeader("validation_report_result", constant("OK"))
                .end()
                .log(LoggingLevel.DEBUG, correlation() + "action_report_result=${header.action_report_result} validation_report_result=${header.validation_report_result}")
                .toD("${header." + Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION + "}")
                .routeId("chouette-process-validation-report");


    }


}



package no.rutebanken.marduk.routes.chouette;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_TYPE;
import static no.rutebanken.marduk.Constants.JSON_PART;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.routes.chouette.json.JobResponse.Status.ABORTED;
import static no.rutebanken.marduk.routes.chouette.json.JobResponse.Status.CANCELED;
import static no.rutebanken.marduk.routes.chouette.json.JobResponse.Status.SCHEDULED;
import static no.rutebanken.marduk.routes.chouette.json.JobResponse.Status.STARTED;

import java.io.InputStream;
import java.net.NoRouteToHostException;
import java.util.Optional;
import java.util.UUID;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.common.base.Strings;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.chouette.json.ActionReportWrapper;
import no.rutebanken.marduk.routes.chouette.json.GtfsImportParameters;
import no.rutebanken.marduk.routes.chouette.json.JobResponse;
import no.rutebanken.marduk.routes.chouette.json.RegtoppImportParameters;
import no.rutebanken.marduk.routes.file.FileType;
import no.rutebanken.marduk.routes.status.Status;
import no.rutebanken.marduk.routes.status.Status.Action;
import no.rutebanken.marduk.routes.status.Status.State;

/**
 * Submits files to Chouette
 */
@Component
public class ChouetteImportRouteBuilder extends BaseRouteBuilder {


	@Value("${chouette.max.retries:3000}")
    private int maxRetries;

    @Value("${chouette.retry.delay:5000}")
    private long retryDelay;

    @Value("${chouette.url}")
    private String chouetteUrl;

    private int consumers = 40; // TODO?

    @SuppressWarnings("unchecked")
	@Override
    public void configure() throws Exception {
        super.configure();

        
//        RedeliveryPolicy chouettePolicy = new RedeliveryPolicy();
//        chouettePolicy.setMaximumRedeliveries(3);
//        chouettePolicy.setRedeliveryDelay(30000);
//        chouettePolicy.setRetriesExhaustedLogLevel(LoggingLevel.ERROR);
//        chouettePolicy.setRetryAttemptedLogLevel(LoggingLevel.WARN);
//        chouettePolicy.setLogExhausted(true);
  
  
    

        onException(HttpOperationFailedException.class, NoRouteToHostException.class)
                .setHeader(Exchange.FILE_NAME, exchangeProperty(Exchange.FILE_NAME))
                .process(e -> Status.addStatus(e, Action.IMPORT, State.FAILED))
                .to("direct:updateStatus")
                .log(LoggingLevel.ERROR, getClass().getName(), "Failed while importing to chouette.")
                .handled(false);


        from("activemq:queue:ChouetteCleanQueue").streamCaching()
	        .log(LoggingLevel.INFO, getClass().getName(), "Starting Chouette dataspace clean for provider: ${header." + PROVIDER_ID + "}")
	        .setHeader(Exchange.FILE_NAME,constant("clean_repository.zip"))
            .setProperty(Exchange.FILE_NAME, header(Exchange.FILE_NAME))
	        .setHeader(Constants.FILE_HANDLE,constant("clean_repository.zip"))
	        .setHeader(Constants.CORRELATION_ID,constant(UUID.randomUUID().toString()))
	        .setHeader(Constants.FILE_TYPE,constant(FileType.REGTOPP.name()))
	        .setHeader(Constants.CLEAN_REPOSITORY, constant(true))
	        .process(e -> Status.addStatus(e, Action.IMPORT, State.PENDING))
	        .to("direct:updateStatus")
            .process(e -> {
                e.getIn().setBody(getClass().getResourceAsStream("/no/rutebanken/marduk/routes/chouette/EMPTY_REGTOPP.zip"));
            })
	        .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential)).id("providerRepository")
	        .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
	        .to("direct:addJson")
	        .routeId("chouette-clean-dataspace");


        from("activemq:queue:ChouetteImportQueue").streamCaching()
                .log(LoggingLevel.INFO, getClass().getName(), "Starting Chouette import for provider: ${header." + PROVIDER_ID + "}")
    	        .setProperty(Constants.CLEAN_REPOSITORY, constant(false))
                .process(e -> Status.addStatus(e, Action.IMPORT, State.PENDING))
                .to("direct:updateStatus")
                .to("direct:getBlob")
                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .to("direct:addJson");

        from("direct:addJson")
                .process(e -> {
                    String fileName = e.getIn().getHeader(Exchange.FILE_NAME, String.class);
                    String fileType = e.getIn().getHeader(Constants.FILE_TYPE, String.class);
                    Long providerId = e.getIn().getHeader(PROVIDER_ID, Long.class);
                    boolean cleanRepository = (boolean) e.getIn().getHeader(Constants.CLEAN_REPOSITORY);
                    e.getIn().setHeader(JSON_PART, getImportParameters(fileName, fileType, providerId,cleanRepository));
                }) //Using header to add json data
                .log(LoggingLevel.DEBUG, getClass().getName(), "import parameters: " + header(JSON_PART))
                .to("direct:sendJobRequest");

        from("direct:sendJobRequest")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Creating multipart request")
                .process(e -> toMultipart(e))
                .setHeader(Exchange.CONTENT_TYPE, simple("multipart/form-data"))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .setProperty("chouette_url", simple(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/importer/${header." + FILE_TYPE + ".toLowerCase()}"))
                .log(LoggingLevel.DEBUG, getClass().getName(), "Calling Chouette with URL: ${exchangeProperty.chouette_url}")
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
                // Attempt to retrigger delivery in case of errors
                .toD("${exchangeProperty.chouette_url}")
//                .onException(HttpOperationFailedException.class)
//                .useOriginalMessage()
//                .onWhen(simple("${exception.message} contains '503' or ${exception.message} contains '500'"))
//                .redeliveryPolicy(chouettePolicy)
                // Redelivery definition complete
                
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Submitted new job")
                .process(e -> {
                    e.getOut().setHeader(Constants.CHOUETTE_JOB_STATUS_URL,e.getIn().getHeader("Location").toString().replaceFirst("http", "http4"));
                    e.getOut().setHeader(Constants.PROVIDER_ID, e.getIn().getHeader(Constants.PROVIDER_ID));
                    e.getOut().setHeader(Constants.CORRELATION_ID, e.getIn().getHeader(Constants.CORRELATION_ID));
                    e.getOut().setHeader(Exchange.FILE_NAME, e.getIn().getHeader(Exchange.FILE_NAME));
                    e.getOut().setHeader(Constants.CHOUETTE_REFERENTIAL, e.getIn().getHeader(Constants.CHOUETTE_REFERENTIAL));
                    e.getOut().setHeader(Constants.CLEAN_REPOSITORY, e.getIn().getHeader(Constants.CLEAN_REPOSITORY));
                    System.out.println(e.getOut().getHeaders());
                })
                .to("activemq:queue:ChouettePollStatusQueue")
                .routeId("chouette-send-import-job");

        from("activemq:queue:ChouettePollStatusQueue?maxConcurrentConsumers=" + consumers)
				.setProperty(Constants.CLEAN_REPOSITORY,header(Constants.CLEAN_REPOSITORY))
				.setProperty(Exchange.FILE_NAME,header(Exchange.FILE_NAME))
        		.setProperty(Constants.CHOUETTE_REFERENTIAL,header(Constants.CHOUETTE_REFERENTIAL))
        		.setProperty("url",header(Constants.CHOUETTE_JOB_STATUS_URL))
                .setProperty("loop_counter", constant(0))
                .setHeader("loop", constant("direct:sendJobStatusRequest"))
                .dynamicRouter().header("loop")
                .log(LoggingLevel.INFO, getClass().getName(), "Done looping ${exchangeProperty.loop_counter} times for status")
                .to("direct:jobStatusDone")
                .routeId("chouette-poll-job-status");


        from("direct:sendJobStatusRequest").streamCaching()
                .log(LoggingLevel.DEBUG, getClass().getName(), "Loop #${exchangeProperty.loop_counter}")
                .removeHeaders("Camel*")
                .setBody(simple(""))
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                .toD("${exchangeProperty.url}")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Got response ${body}")
                .unmarshal().json(JsonLibrary.Jackson, JobResponse.class)
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Unmarshalled: ${body}")
                .process(e -> {
                    JobResponse response = e.getIn().getBody(JobResponse.class);
                    if (!(response.status.equals(SCHEDULED)
                            || response.status.equals(STARTED))
                            || e.getProperty("loop_counter", Integer.class) >= maxRetries - 1) {
                        e.getIn().setHeader("loop", null);
                    } else {
                        e.getIn().setHeader("loop", "direct:sendDelayedJobStatusRequest");
                    }
                    e.getIn().setHeader("current_status", response.status.name());
                    e.setProperty("loop_counter", e.getProperty("loop_counter", Integer.class) + 1);
                })
                .choice()
                .when(simple("${header.current_status} == '" + STARTED + "'"))
                    .setHeader(Exchange.FILE_NAME, exchangeProperty(Exchange.FILE_NAME))
                    .process(e -> Status.addStatus(e, Action.IMPORT, State.STARTED))
                    .to("direct:updateStatus")
                .end()
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")               
                .routeId("chouette-get-job-status");



        from("direct:sendDelayedJobStatusRequest")
        	.log(LoggingLevel.DEBUG, getClass().getName(), "Sleeping " + retryDelay + " ms...")
        	.delayer(retryDelay)
        	.to("direct:sendJobStatusRequest");

        from("direct:jobStatusDone").streamCaching()
                .log(LoggingLevel.DEBUG, getClass().getName(), "Exited retry loop with status ${header.current_status}")
                .setHeader(Exchange.FILE_NAME, exchangeProperty(Exchange.FILE_NAME))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .choice()
                .when(simple("${header.current_status} == '" + SCHEDULED + "' || ${header.current_status} == '" + STARTED + "'"))
                    .log(LoggingLevel.WARN, getClass().getName(), "Timed out with state ${header.current_status}. Config should probably be tweaked. Stopping route.")
                    .process(e -> Status.addStatus(e, Action.IMPORT, State.TIMEOUT))
                    .to("direct:updateStatus")
                    .stop()
                .when(simple("${header.current_status} == '" + ABORTED + "' || ${header.current_status} == '" + CANCELED + "'"))
                    .log(LoggingLevel.WARN, getClass().getName(), "Import ended in state ${header.current_status}. Stopping route.")
                    .process(e -> Status.addStatus(e, Action.IMPORT, State.FAILED))
                    .to("direct:updateStatus")
                    .stop()
                .end()
                .process(e -> {
                    JobResponse response = e.getIn().getBody(JobResponse.class);
                    Optional<String> actionReportUrlOptional = response.links.stream().filter(li -> "action_report".equals(li.rel)).findFirst().map(li -> li.href.replaceFirst("http", "http4"));
                    e.setProperty("action_report_url", actionReportUrlOptional.orElseThrow(() -> new IllegalArgumentException("No URL found for action report.")));
                    Optional<String> validationReportUrlOptional = response.links.stream().filter(li -> "validation_report".equals(li.rel)).findFirst().map(li -> li.href.replaceFirst("http", "http4"));
                    e.setProperty("validation_report_url", validationReportUrlOptional.orElseThrow(() -> new IllegalArgumentException("No URL found for validation report.")));
                })
                // Fetch and parse action report
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Calling action report url ${exchangeProperty.action_report_url}")
                .removeHeaders("Camel*")
                .setBody(simple(""))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .toD("${exchangeProperty.action_report_url}")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .unmarshal().json(JsonLibrary.Jackson, ActionReportWrapper.class)
                .process(e -> {
                    e.setProperty("action_report_result", e.getIn().getBody(ActionReportWrapper.class).actionReport.result);
                })
                // Fetch and parse validation report
                .log(LoggingLevel.DEBUG, getClass().getName(), "Calling validation report url ${exchangeProperty.validation_report_url}")
                .removeHeaders("Camel*")
                .setBody(simple(""))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .toD("${exchangeProperty.validation_report_url}")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .to("direct:checkValidationReport")
                .routeId("chouette-process-action-report");

        from("direct:checkValidationReport")
        		.choice()
        		.when().jsonpath("$.validation_report.tests[?(@.severity == 'ERROR' && @.result == 'NOK')]")
       				.setProperty("validation_report_result", constant("NOK"))
		    	.otherwise()
		    		.setProperty("validation_report_result",constant("OK"))
		    	.end()
	        	.log(LoggingLevel.INFO, getClass().getName(), "action_report_result=${exchangeProperty.action_report_result} validation_report_result=${exchangeProperty.validation_report_result}")
		        .to("direct:processActionReportResult")
                .routeId("chouette-process-validation-report");
    

		 from("direct:processActionReportResult")
		        .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
		        .setHeader(Exchange.FILE_NAME, exchangeProperty(Exchange.FILE_NAME))
		        .setBody(constant(""))
		        .choice()
		        .when(simple("${exchangeProperty.action_report_result} == 'OK' and ${exchangeProperty.validation_report_result} == 'OK'"))
		            .to("direct:checkScheduledJobsBeforeTriggeringExport")
		            .process(e -> Status.addStatus(e, Action.IMPORT, State.OK))
		        .when(simple("${exchangeProperty.action_report_result} == 'OK' and ${exchangeProperty.validation_report_result} == 'NOK'"))
		        	.log(LoggingLevel.INFO, getClass().getName(), "Import ok but validation failed")
		            .process(e -> Status.addStatus(e, Action.IMPORT, State.FAILED))
		        .when(simple("${exchangeProperty.action_report_result} == 'NOK'"))
		        	.choice()
		    		.when(simple("${exchangeProperty.action_report_result} == 'NOK' && ${exchangeProperty."+Constants.CLEAN_REPOSITORY+"} == true"))
		        		.log(LoggingLevel.WARN, getClass().getName(), "Clean ok.")
		        		.process(e -> Status.addStatus(e, Action.IMPORT, State.OK))
		    		.when(simple("${exchangeProperty.action_report_result} == 'NOK'"))
		        		.log(LoggingLevel.WARN, getClass().getName(), "Import not ok.")
		        		.process(e -> Status.addStatus(e, Action.IMPORT, State.FAILED))
		        	.endChoice()
		        .otherwise()
		            .log(LoggingLevel.WARN, getClass().getName(), "Something went wrong")
		            .process(e -> Status.addStatus(e, Action.IMPORT, State.FAILED))
		        .end()
		        .to("direct:updateStatus")
		        .routeId("chouette-process-job-status");
	
		 // Check that no other import jobs in status SCHEDULED exists for this referential. If so, do not trigger export
		from("direct:checkScheduledJobsBeforeTriggeringExport")
			.setProperty("job_status_url",simple("{{chouette.url}}/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/jobs?action=importer"))
			.toD("${exchangeProperty.job_status_url}")
			.choice()
			.when().jsonpath("$.*[?(@.status == 'SCHEDULED')].status")
				.log(LoggingLevel.INFO, getClass().getName(), "Import and validation ok, skipping export as there are more import jobs active")
            .otherwise()
				.log(LoggingLevel.INFO, getClass().getName(), "Import and validation ok, triggering GTFS export.")
		        .setBody(constant(""))
				.to("activemq:queue:ChouetteExportQueue")
            .end()
            .routeId("chouette-process-job-list");
		
    }

    void toMultipart(Exchange exchange) {
        String fileName = exchange.getIn().getHeader(FILE_HANDLE, String.class);
        if (Strings.isNullOrEmpty(fileName)) {
            throw new IllegalArgumentException("No file name");
        }

        String jsonPart = exchange.getIn().getHeader(JSON_PART, String.class);
        if (Strings.isNullOrEmpty(jsonPart)) {
            throw new IllegalArgumentException("No json data");
        }

        InputStream inputStream = exchange.getIn().getBody(InputStream.class);
        if (inputStream == null) {
            throw new IllegalArgumentException("No data");
        }

        MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
        entityBuilder.addBinaryBody("parameters", exchange.getIn().getHeader(JSON_PART, String.class).getBytes(), ContentType.DEFAULT_BINARY, "parameters.json");
        entityBuilder.addBinaryBody("feed", inputStream, ContentType.DEFAULT_BINARY, fileName);

        exchange.getOut().setBody(entityBuilder.build());
        exchange.getOut().setHeaders(exchange.getIn().getHeaders());
    }

    private Object getImportParameters(String fileName, String fileType, Long providerId,boolean cleanRepository) {
        if (FileType.REGTOPP.name().equals(fileType)){
            return getRegtoppImportParametersAsString(fileName, providerId, cleanRepository);
        } else if (FileType.GTFS.name().equals(fileType)) {
            return getGtfsImportParametersAsString(fileName, providerId, cleanRepository);
        } else {
            throw new IllegalArgumentException("Cannot create import parameters from file type '" + fileType + "'");
        }
    }

    String getRegtoppImportParametersAsString(String importName, Long providerId, boolean cleanRepository) {
        ChouetteInfo chouetteInfo = getProviderRepository().getProvider(providerId).chouetteInfo;
        if (!chouetteInfo.usesRegtopp()){
            throw new IllegalArgumentException("Could not get regtopp information about provider '" + providerId + "'.");
        }
        RegtoppImportParameters regtoppImportParameters = RegtoppImportParameters.create(importName, chouetteInfo.prefix,
                chouetteInfo.referential, chouetteInfo.organisation, chouetteInfo.user, chouetteInfo.regtoppVersion, chouetteInfo.regtoppCoordinateProjection,chouetteInfo.regtoppCalendarStrategy,cleanRepository,chouetteInfo.enableValidation);
        return regtoppImportParameters.toJsonString();
    }

    String getGtfsImportParametersAsString(String importName, Long providerId, boolean cleanRepository) {
        ChouetteInfo chouetteInfo = getProviderRepository().getProvider(providerId).chouetteInfo;
        GtfsImportParameters gtfsImportParameters = GtfsImportParameters.create(importName, chouetteInfo.prefix, chouetteInfo.referential, chouetteInfo.organisation, chouetteInfo.user,cleanRepository,chouetteInfo.enableValidation);
        return gtfsImportParameters.toJsonString();
    }



}



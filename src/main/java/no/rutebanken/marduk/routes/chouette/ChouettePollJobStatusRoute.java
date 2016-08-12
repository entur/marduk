package no.rutebanken.marduk.routes.chouette;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.routes.chouette.json.Status.ABORTED;
import static no.rutebanken.marduk.routes.chouette.json.Status.CANCELED;
import static no.rutebanken.marduk.routes.chouette.json.Status.SCHEDULED;
import static no.rutebanken.marduk.routes.chouette.json.Status.STARTED;

import java.util.List;
import java.util.Optional;

import org.apache.activemq.ScheduledMessage;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.chouette.json.ActionReportWrapper;
import no.rutebanken.marduk.routes.chouette.json.JobResponse;
import no.rutebanken.marduk.routes.chouette.json.JobResponseWithLinks;
import no.rutebanken.marduk.routes.status.Status;
import no.rutebanken.marduk.routes.status.Status.Action;
import no.rutebanken.marduk.routes.status.Status.State;

@Component
public class ChouettePollJobStatusRoute extends BaseRouteBuilder {


	@Value("${chouette.max.retries:3000}")
    private int maxRetries;

    @Value("${chouette.retry.delay:15000}")
    private long retryDelay;

    @Value("${chouette.url}")
    private String chouetteUrl;

    private int maxConsumers = 5; 


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

//        onException(HttpOperationFailedException.class, NoRouteToHostException.class)
//                .setHeader(Constants.FILE_NAME, exchangeProperty(Constants.FILE_NAME))
//                .process(e -> Status.addStatus(e, Action.valueOf( (String) e.getIn().getHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE)), State.FAILED))
//                .to("direct:updateStatus")
//                .log(LoggingLevel.ERROR,correlation()+"Failed while polling chouette.")
//                .handled(true);
        
        from("direct:chouetteGetJobs")
        		.process( e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .removeHeaders("Camel*")
                .setBody(constant(""))
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                .setProperty("chouette_url", simple(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/jobs"))
                .process(e -> {
                	String url = (String) e.getProperty("chouette_url");
                	
                	URIBuilder b = new URIBuilder(url);
                	if(e.getIn().getHeader("action") != null) {
                		b.addParameter("action", (String) e.getIn().getHeader("action"));
                	}
                	if(e.getIn().getHeader("status") != null) {
                		
                		Object status = e.getIn().getHeader("status");
                		if(status instanceof List) {
                    		for(String s : (List<String>)status) {
                        		b.addParameter("status", s);
                    		}
                		} else            		{
                			b.addParameter("status", (String) status);
                		}
                	}
                	
                	e.setProperty("chouette_url", b.toString());
                })
                .toD("${exchangeProperty.chouette_url}")
                .unmarshal().json(JsonLibrary.Jackson, JobResponse[].class)
                .routeId("chouette-list-jobs");
        
        
        from("activemq:queue:ChouettePollStatusQueue?transacted=true&maxConcurrentConsumers=" + maxConsumers)
				.validate(header(Constants.CORRELATION_ID).isNotNull())
				.validate(header(Constants.PROVIDER_ID).isNotNull())
				.validate(header(Constants.FILE_NAME).isNotNull())
				.validate(header(Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION).isNotNull())
				.validate(header(Constants.CHOUETTE_JOB_STATUS_URL).isNotNull())
				.validate(header(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE).isNotNull())
				.to("direct:checkJobStatus")
				.routeId("chouette-validate-job-status-parameters");
				
		from("direct:checkJobStatus")
		.validate(header(Constants.FILE_NAME).isNotNull())
        		.process(e -> {
              		e.getIn().setHeader("loopCounter", (Integer)e.getIn().getHeader("loopCounter",0) + 1);
        		})
				.setProperty(Constants.CLEAN_REPOSITORY,header(Constants.CLEAN_REPOSITORY))
        		.setProperty(Constants.CHOUETTE_REFERENTIAL,header(Constants.CHOUETTE_REFERENTIAL))
        		.setProperty("url",header(Constants.CHOUETTE_JOB_STATUS_URL))
                .removeHeaders("Camel*")
                .setBody(constant(""))
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                .toD("${exchangeProperty.url}")
                .unmarshal().json(JsonLibrary.Jackson, JobResponse.class)
                .setProperty("current_status",simple("${body.status}"))
                .choice()
                .when(PredicateBuilder.or(simple("${body.status} != ${type:no.rutebanken.marduk.routes.chouette.json.Status.SCHEDULED} && ${body.status} != ${type:no.rutebanken.marduk.routes.chouette.json.Status.STARTED}"),
                		simple("${header.loopCounter} > "+maxRetries)))
                	.to("direct:jobStatusDone")
                .otherwise()
                	// Update status
                	.choice()
                    .when(simple("${exchangeProperty.current_status} == '" + STARTED + "' && ${header.loopCounter} == 1"))
	                    .process(e -> Status.addStatus(e,Action.valueOf( (String) e.getIn().getHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE)), State.STARTED))
	                    .to("direct:updateStatus")
	                .end()
                  	.setHeader(ScheduledMessage.AMQ_SCHEDULED_DELAY,constant(retryDelay))
                  	// Remove or ActiveMQ will think message is overdue and resend immediately
                    .removeHeader("scheduledJobId")
                  	.setBody(constant(""))
                    //.log(LoggingLevel.INFO,"Scheduling next polling message in ${header."+ActiveMQMessage.AMQ_SCHEDULED_DELAY+"}ms")
                	.to("activemq:queue:ChouettePollStatusQueue")
                .end()
                .routeId("chouette-get-job-status");
       

        from("direct:jobStatusDone")
		.validate(header(Constants.FILE_NAME).isNotNull())
                .log(LoggingLevel.DEBUG,correlation()+"Exited retry loop with status ${header.current_status}")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .choice()
                .when(simple("${header.current_status} == '" + SCHEDULED + "' || ${header.current_status} == '" + STARTED + "'"))
                    .log(LoggingLevel.WARN,correlation()+"Job timed out with state ${header.current_status}. Config should probably be tweaked. Stopping route.")
                    .process(e -> Status.addStatus(e, Action.valueOf( (String) e.getIn().getHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE)), State.TIMEOUT))
                    .to("direct:updateStatus")
                    .stop()
                .when(simple("${header.current_status} == '" + ABORTED + "' || ${header.current_status} == '" + CANCELED + "'"))
                    .log(LoggingLevel.WARN,correlation()+"Job ended in state ${header.current_status}. Stopping route.")
                    .process(e -> Status.addStatus(e, Action.valueOf( (String) e.getIn().getHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE)), State.FAILED))
                    .to("direct:updateStatus")
                    .stop()
                .end()
                .process(e -> {
                    JobResponseWithLinks response = e.getIn().getBody(JobResponseWithLinks.class);
                    Optional<String> actionReportUrlOptional = response.links.stream().filter(li -> "action_report".equals(li.rel)).findFirst().map(li -> li.href.replaceFirst("http", "http4"));
                    e.getIn().setHeader("action_report_url", actionReportUrlOptional.orElseThrow(() -> new IllegalArgumentException("No URL found for action report.")));
                    Optional<String> validationReportUrlOptional = response.links.stream().filter(li -> "validation_report".equals(li.rel)).findFirst().map(li -> li.href.replaceFirst("http", "http4"));
                    e.getIn().setHeader("validation_report_url", validationReportUrlOptional.orElse(null));
                    Optional<String> dataUrlOptional = response.links.stream().filter(li -> "data".equals(li.rel)).findFirst().map(li -> li.href.replaceFirst("http", "http4"));
                    e.getIn().setHeader("data_url", dataUrlOptional.orElse(null));
                })
                // Fetch and parse action report
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Calling action report url ${header.action_report_url}")
                .removeHeaders("Camel*")
                .setBody(simple(""))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .toD("${header.action_report_url}")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .unmarshal().json(JsonLibrary.Jackson, ActionReportWrapper.class)
                .process(e -> {
                	e.getIn().setHeader("action_report_result", e.getIn().getBody(ActionReportWrapper.class).actionReport.result);
                })

	        	// Fetch and parse validation report
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .choice()
                .when(simple("${header.validation_report_url} != null"))
	                .log(LoggingLevel.DEBUG,correlation()+"Calling validation report url ${header.validation_report_url}")
	                .removeHeaders("Camel*")
	                .setBody(simple(""))
	                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
	                .toD("${header.validation_report_url}")
	                .to("direct:checkValidationReport")
	            .otherwise()
	            	.setHeader("validation_report_result",constant("NOT_PRESENT"))
		        	.toD("${header."+Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION+"}")
		        .end()
                .routeId("chouette-process-job-reports");

        from("direct:checkValidationReport")
        		.choice()
        		.when().jsonpath("$.validation_report.tests[?(@.severity == 'ERROR' && @.result == 'NOK')]")
       				.setHeader("validation_report_result", constant("NOK"))
		    	.otherwise()
		    		.setHeader("validation_report_result",constant("OK"))
		    	.end()
	        	.log(LoggingLevel.DEBUG,correlation()+"action_report_result=${header.action_report_result} validation_report_result=${header.validation_report_result}")
	        	.toD("${header."+Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION+"}")
                .routeId("chouette-process-validation-report");
    
        
    }
}



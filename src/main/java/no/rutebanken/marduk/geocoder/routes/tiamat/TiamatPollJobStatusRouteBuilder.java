package no.rutebanken.marduk.geocoder.routes.tiamat;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.geocoder.routes.tiamat.xml.ExportJob;
import no.rutebanken.marduk.geocoder.routes.tiamat.xml.JobStatus;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.SystemStatus;
import org.apache.activemq.ScheduledMessage;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.geocoder.GeoCoderConstants.*;

@Component
public class TiamatPollJobStatusRouteBuilder extends BaseRouteBuilder {


	@Value("${tiamat.max.retries:3000}")
	private int maxRetries;

	@Value("${tiamat.retry.delay:15000}")
	private long retryDelay;

	@Value("${tiamat.url}")
	private String tiamatUrl;

	@Override
	public void configure() throws Exception {
		super.configure();

		from(TIAMAT_EXPORT_POLL.getEndpoint())
				.validate(header(Constants.JOB_ID).isNotNull())
				.validate(header(Constants.JOB_STATUS_URL).isNotNull())
				.validate(header(Constants.JOB_STATUS_ROUTING_DESTINATION).isNotNull())
				.to("direct:checkTiamatJobStatus")
				.routeId("tiamat-validate-job-status-parameters");

		from("direct:checkTiamatJobStatus")
				.process(e -> {
					e.getIn().setHeader("loopCounter", (Integer) e.getIn().getHeader("loopCounter", 0) + 1);
				})
				.removeHeaders("Camel*")
				.setBody(constant(""))
				.setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
				.toD(tiamatUrl + "/${header." + Constants.JOB_STATUS_URL + "}")
				.convertBodyTo(ExportJob.class)
				.setHeader("current_status", simple("${body.status}"))
				.choice()
				.when(PredicateBuilder.or(simple("${header.current_status} != '" + JobStatus.PROCESSING + "'"),
						simple("${header.loopCounter} > " + maxRetries)))
				.to("direct:tiamatJobStatusDone")
				.otherwise()

				.setHeader(ScheduledMessage.AMQ_SCHEDULED_DELAY, constant(retryDelay))
				// Remove or ActiveMQ will think message is overdue and resend immediately
				.removeHeader("scheduledJobId")
				.setProperty(GEOCODER_NEXT_TASK, simple("${header." + GEOCODER_CURRENT_TASK + "}"))
				.end()
				.routeId("tiamat-get-job-status");


		from("direct:tiamatJobStatusDone")
				.log(LoggingLevel.DEBUG, correlation() + " exited retry loop with status ${header.current_status}")
				.choice()
				.when(simple("${header.current_status} == '" + JobStatus.PROCESSING + "'"))
				.log(LoggingLevel.WARN, correlation() + " timed out with state ${header.current_status}. Config should probably be tweaked. Not rescheduling.")
				.process(e -> SystemStatus.builder(e).state(SystemStatus.State.TIMEOUT).build()).to("direct:updateSystemStatus")
				.when(simple("${header.current_status} == '" + JobStatus.FAILED + "'"))
				.log(LoggingLevel.WARN, correlation() + " ended in state ${header.current_status}. Not rescheduling.")
				.process(e -> SystemStatus.builder(e).state(SystemStatus.State.FAILED).build()).to("direct:updateSystemStatus")
				.end()
				.toD("${header." + Constants.JOB_STATUS_ROUTING_DESTINATION + "}")
				.end()
				.routeId("tiamat-process-job-status-done");
	}


	@Override
	protected String correlation() {
		return "Job [id:${header." + Constants.JOB_ID + "}] ";
	}
}

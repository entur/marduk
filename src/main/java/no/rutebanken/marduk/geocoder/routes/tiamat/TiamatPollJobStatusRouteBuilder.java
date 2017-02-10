package no.rutebanken.marduk.geocoder.routes.tiamat;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.geocoder.tiamat.xml.ExportJob;
import no.rutebanken.marduk.geocoder.tiamat.xml.JobStatus;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.activemq.ScheduledMessage;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TiamatPollJobStatusRouteBuilder extends BaseRouteBuilder {


	@Value("${tiamat.max.retries:3000}")
	private int maxRetries;

	@Value("${tiamat.retry.delay:15000}")
	private long retryDelay;

	@Value("${tiamat.url}")
	private String tiamatUrl;

	private int maxConsumers = 5;

	@Override
	public void configure() throws Exception {
		super.configure();

		from("activemq:queue:TiamatPollStatusQueue?transacted=true&maxConcurrentConsumers=" + maxConsumers)
				.transacted()
				.validate(header(Constants.JOB_ID).isNotNull())
				.validate(header(Constants.JOB_STATUS_URL).isNotNull())
				.validate(header(Constants.JOB_STATUS_ROUTING_DESTINATION).isNotNull())
				.to("direct:checkTiamatJobStatus")
				.routeId("tiamat-validate-job-status-parameters");

		from("direct:checkTiamatJobStatus")
				.process(e -> {
					e.getIn().setHeader("loopCounter", (Integer) e.getIn().getHeader("loopCounter", 0) + 1);
				})
				.setProperty("url", header(Constants.JOB_STATUS_URL))
				.removeHeaders("Camel*")
				.setBody(constant(""))
				.setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
				.process(e ->
						         e.getIn().getBody()
				)
				.toD("${exchangeProperty.url}")
				.convertBodyTo(ExportJob.class)
				.setProperty("current_status", simple("${body.status}"))
				.choice()
				.when(PredicateBuilder.or(simple("${body.status} != ${type:no.rutebanken.marduk.geocoder.tiamat.xml.JobStatus.Processing}"),
						simple("${header.loopCounter} > " + maxRetries)))
				.to("direct:tiamatJobStatusDone")
				.otherwise()

				.setHeader(ScheduledMessage.AMQ_SCHEDULED_DELAY, constant(retryDelay))
				// Remove or ActiveMQ will think message is overdue and resend immediately
				.removeHeader("scheduledJobId")
				.setBody(constant(""))
				.to("activemq:queue:TiamatPollStatusQueue")
				.end()
				.routeId("tiamat-get-job-status");


		from("direct:tiamatJobStatusDone")
				.log(LoggingLevel.DEBUG, correlation() + " exited retry loop with status ${header.current_status}")
				.choice()
				.when(simple("${header.current_status} == '" + JobStatus.PROCESSING + "'"))
				.log(LoggingLevel.WARN, correlation() + " timed out with state ${header.current_status}. Config should probably be tweaked. Stopping route.")
// TODO event?
				.stop()
				.when(simple("${header.current_status} == '" + JobStatus.FAILED + "'"))
				.log(LoggingLevel.WARN, correlation() + " ended in state ${header.current_status}. Stopping route.")
// TODO event?
				.stop()
				.end()
				.toD("${header." + Constants.JOB_STATUS_ROUTING_DESTINATION + "}")
// TODO event?
				.stop()
				.end()
				.routeId("tiamat-process-job-status-done");
	}


	@Override
	protected String correlation() {
		return "Job [id:${header." + Constants.JOB_ID + "}] ";
	}
}

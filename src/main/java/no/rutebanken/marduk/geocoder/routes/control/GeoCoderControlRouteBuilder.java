package no.rutebanken.marduk.geocoder.routes.control;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.SystemStatus;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Date;

import static no.rutebanken.marduk.Constants.LOOP_COUNTER;
import static no.rutebanken.marduk.geocoder.GeoCoderConstants.*;


@Component
public class GeoCoderControlRouteBuilder extends BaseRouteBuilder {


	private static final String TASK_MESSAGE = "RutebankenGeoCoderTaskMessage";

	@Value("${geocoder.max.retries:3000}")
	private int maxRetries;

	@Value("${geocoder.retry.delay:15000}")
	private int retryDelay;

	@Override
	public void configure() throws Exception {
		super.configure();

		from("direct:geoCoderStart")
				.process(e -> e.getIn().setBody(new GeoCoderTaskMessage(e.getIn().getBody(GeoCoderTask.class)).toString()))
				.to("activemq:queue:GeoCoderQueue")
				.routeId("geocoder-start");

		singletonFrom("activemq:queue:GeoCoderQueue?transacted=true&messageListenerContainerFactoryRef=batchListenerContainerFactory")
				.autoStartup("{{geocoder.autoStartup:false}}")
				.transacted()
				.to("direct:geoCoderMergeTaskMessages")
				.setProperty(TASK_MESSAGE, simple("${body}"))
				.to("direct:geoCoderRehydrate")

				.to("direct:geoCoderDelayIfRetry")
				.log(LoggingLevel.INFO, getClass().getName(), "Processing: ${body}. QueuedTasks: ${exchangeProperty." + TASK_MESSAGE + ".tasks.size}")
				.toD("${body.endpoint}")
				.setBody(simple("${exchangeProperty." + TASK_MESSAGE + "}"))

				.choice()
				.when(simple("${exchangeProperty." + GEOCODER_RESCHEDULE_TASK + "}"))
				.to("direct:geoCoderRescheduleTask")
				.otherwise()
				.removeHeader(LOOP_COUNTER)
				.end()

				.to("direct:geoCoderDehydrate")

				.choice()
				.when(simple("${body.complete}"))
				.log(LoggingLevel.INFO, getClass().getName(), "GeoCoder route completed")
				.otherwise()
				.convertBodyTo(String.class)
				.to("activemq:queue:GeoCoderQueue")
				.end()

				.routeId("geocoder-main-route");

		from("direct:geoCoderDelayIfRetry")
				.choice()
				.when(simple("${header." + LOOP_COUNTER + "} > 0"))
				.log(LoggingLevel.INFO, getClass().getName(), "Delay processing of: ${body}. Retry no: ${header." + LOOP_COUNTER + "}")
				.delay(retryDelay)
				.end()
				.routeId("geocoder-delay-retry");

		from("direct:geoCoderMergeTaskMessages")
				.process(e -> e.getIn().setBody(merge(e.getIn().getBody(Collection.class))))
				.routeId("geocoder-merge-messages");

		from("direct:geoCoderRehydrate")
				.process(e -> rehydrate(e))
				.routeId("geocoder-rehydrate-task");

		from("direct:geoCoderDehydrate")
				.process(e -> dehydrate(e))
				.routeId("geocoder-dehydrate-task");

		from("direct:geoCoderRescheduleTask")
				.process(e -> e.getIn().setHeader(LOOP_COUNTER, (Integer) e.getIn().getHeader(LOOP_COUNTER, 0) + 1))
				.choice()
				.when(simple("${header." + LOOP_COUNTER + "} > " + maxRetries))
				.log(LoggingLevel.WARN, getClass().getName(), "${header." + GEOCODER_CURRENT_TASK + "} timed out with state ${header.current_status}. Config should probably be tweaked. Not rescheduling.")
				.process(e -> SystemStatus.builder(e).state(SystemStatus.State.TIMEOUT).build()).to("direct:updateSystemStatus")
				.otherwise()
				.setProperty(GEOCODER_NEXT_TASK, simple("${header." + GEOCODER_CURRENT_TASK + "}"))
				.end()
				.routeId("geocoder-reschedule-task");
	}


	private void rehydrate(Exchange e) {
		GeoCoderTaskMessage msg = e.getIn().getBody(GeoCoderTaskMessage.class);
		GeoCoderTask task = msg.popNextTask();
		task.getHeaders().forEach((k, v) -> e.getIn().setHeader(k, v));
		e.setProperty(GEOCODER_CURRENT_TASK, task);
		e.getIn().setBody(task);
	}


	private void dehydrate(Exchange e) {
		GeoCoderTask nextTask = e.getProperty(GEOCODER_NEXT_TASK, GeoCoderTask.class);
		if (nextTask != null) {
			e.getIn().getBody(GeoCoderTaskMessage.class).addTask(nextTask);
			e.getIn().getHeaders().entrySet().stream().filter(entry -> entry.getKey().startsWith("Rutebanken")).forEach(entry -> nextTask.getHeaders().put(entry.getKey(), entry.getValue()));
		}
	}


	private GeoCoderTaskMessage merge(Collection<ActiveMQTextMessage> messages) {
		GeoCoderTaskMessage merged = new GeoCoderTaskMessage();

		if (!CollectionUtils.isEmpty(messages)) {
			for (ActiveMQTextMessage msg : messages) {
				try {
					// TODO merge smarter, keep oldest. log discard?
					GeoCoderTaskMessage taskMessage = GeoCoderTaskMessage.fromString(msg.getText());
					merged.getTasks().addAll(taskMessage.getTasks());
				} catch (Exception e) {
					log.warn("Discarded unparseable text msg: " + msg + ". Exception:" + e.getMessage(), e);
				}
			}
		}

		return merged;
	}

}

package no.rutebanken.marduk.geocoder.routes.control;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collection;

import static no.rutebanken.marduk.geocoder.GeoCoderConstants.GEOCODER_CURRENT_TASK;
import static no.rutebanken.marduk.geocoder.GeoCoderConstants.GEOCODER_NEXT_TASK;


@Component
public class GeoCoderControlRouteBuilder extends BaseRouteBuilder {


	private static final String TASK_MESSAGE = "RutebankenGeoCoderTaskMessage";

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
				.log(LoggingLevel.INFO, getClass().getName(), "Processing: ${body}. QueuedTasks: ${exchangeProperty." + TASK_MESSAGE + ".tasks.size}")
				.toD("${body.endpoint}")
				.setBody(simple("${exchangeProperty." + TASK_MESSAGE + "}"))
				.to("direct:geoCoderDehydrate")
				.choice()
				.when(simple("${body.complete}"))
				.log(LoggingLevel.INFO, getClass().getName(), "GeoCoder route completed")
				.otherwise()
				.convertBodyTo(String.class)
				.to("activemq:queue:GeoCoderQueue")
				.routeId("geocoder-main-route");


		from("direct:geoCoderMergeTaskMessages")
				.process(e -> e.getIn().setBody(merge(e.getIn().getBody(Collection.class))))
				.routeId("geocoder-merge-messages");

		from("direct:geoCoderRehydrate")
				.process(e -> rehydrate(e))
				.routeId("geocoder-rehydrate-task");

		from("direct:geoCoderDehydrate")
				.process(e -> dehydrate(e))
				.routeId("geocoder-dehydrate-task");

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

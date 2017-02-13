package no.rutebanken.marduk.routes.status;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static no.rutebanken.marduk.Constants.*;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class StatusEvent {
	public enum Action {FILE_TRANSFER, EXPORT, UPDATE, BUILD_GRAPH}

	public enum State {STARTED, TIMEOUT, FAILED, OK}

	@JsonProperty("correlation_id")
	private String correlationId;

	@JsonProperty("action")
	private StatusEvent.Action action;

	@JsonProperty("state")
	private StatusEvent.State state;

	@JsonProperty("entity")
	private String entity;

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS", timezone = "CET")
	@JsonProperty("date")
	private Date date;


	private StatusEvent() {
	}

	public String toString() {
		try {
			ObjectMapper mapper = new ObjectMapper();
			StringWriter writer = new StringWriter();
			mapper.writeValue(writer, this);
			return writer.toString();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static StatusEvent.Builder builder() {
		return new StatusEvent.Builder();
	}

	public static StatusEvent.Builder builder(Exchange exchange) {
		return new StatusEvent.ExchangeBuilder(exchange);
	}

	public static class Builder {

		protected StatusEvent statusEvent = new StatusEvent();

		private Builder() {
		}

		public StatusEvent.Builder action(StatusEvent.Action action) {
			statusEvent.action = action;
			return this;
		}

		public StatusEvent.Builder state(StatusEvent.State state) {
			statusEvent.state = state;
			return this;
		}

		public StatusEvent.Builder start(Action action) {
			statusEvent.correlationId = UUID.randomUUID().toString();
			statusEvent.action = action;
			statusEvent.state = State.STARTED;
			return this;
		}

		public StatusEvent.Builder entity(String entity) {
			statusEvent.entity = entity;
			return this;
		}

		public StatusEvent build() {
			if (statusEvent.correlationId == null) {
				throw new IllegalArgumentException("No correlation id");
			}

			if (statusEvent.action == null) {
				throw new IllegalArgumentException("No action");
			}

			if (statusEvent.state == null) {
				throw new IllegalArgumentException("No state");
			}

			statusEvent.date = Date.from(Instant.now(Clock.systemDefaultZone()));
			return statusEvent;
		}
	}

	public static class ExchangeBuilder extends StatusEvent.Builder {

		private Exchange exchange;

		private ExchangeBuilder(Exchange exchange) {
			super();
			this.exchange = exchange;
			statusEvent.correlationId = exchange.getIn().getHeader(STATUS_EVENT_CORRELATION_ID, String.class);
			String actionString = exchange.getIn().getHeader(STATUS_EVENT_ACTION, String.class);
			if (actionString != null) {
				statusEvent.action = Action.valueOf(actionString);
			}
			statusEvent.entity = exchange.getIn().getHeader(STATUS_EVENT_ENTITY, String.class);
		}


		@Override
		public StatusEvent build() {
			if (exchange == null) {
				throw new IllegalStateException(this.getClass() + " does not hold an instance of exchange.");
			}

			StatusEvent status = super.build();
			exchange.getIn().setHeader(STATUS_EVENT_CORRELATION_ID, statusEvent.correlationId);
			exchange.getIn().setHeader(STATUS_EVENT_ACTION, statusEvent.action.toString());
			exchange.getIn().setHeader(STATUS_EVENT_ENTITY, statusEvent.entity);

			exchange.getOut().setBody(status.toString());
			exchange.getOut().setHeaders(exchange.getIn().getHeaders());
			return status;
		}
	}
}

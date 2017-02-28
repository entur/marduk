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
public class SystemStatus {
	public enum Action {FILE_TRANSFER, EXPORT, UPDATE, BUILD_GRAPH}

	public enum State {STARTED, TIMEOUT, FAILED, OK}

	@JsonProperty("correlation_id")
	private String correlationId;

	@JsonProperty("action")
	private SystemStatus.Action action;

	@JsonProperty("state")
	private SystemStatus.State state;

	@JsonProperty("entity")
	private String entity;

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS", timezone = "CET")
	@JsonProperty("date")
	private Date date;


	private SystemStatus() {
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

	public String getCorrelationId() {
		return correlationId;
	}

	public Action getAction() {
		return action;
	}

	public State getState() {
		return state;
	}

	public String getEntity() {
		return entity;
	}

	public Date getDate() {
		return date;
	}

	public static SystemStatus.Builder builder() {
		return new SystemStatus.Builder();
	}

	public static SystemStatus.Builder builder(Exchange exchange) {
		return new SystemStatus.ExchangeBuilder(exchange);
	}

	public static class Builder {

		protected SystemStatus systemStatus = new SystemStatus();

		private Builder() {
		}

		public SystemStatus.Builder action(SystemStatus.Action action) {
			systemStatus.action = action;
			return this;
		}

		public SystemStatus.Builder state(SystemStatus.State state) {
			systemStatus.state = state;
			return this;
		}

		public SystemStatus.Builder start(Action action) {
			systemStatus.correlationId = UUID.randomUUID().toString();
			systemStatus.action = action;
			systemStatus.state = State.STARTED;
			return this;
		}

		public SystemStatus.Builder entity(String entity) {
			systemStatus.entity = entity;
			return this;
		}

		public SystemStatus.Builder correlationId(String correlationId) {
			systemStatus.correlationId = correlationId;
			return this;
		}
		public SystemStatus build() {
			if (systemStatus.correlationId == null) {
				throw new IllegalArgumentException("No correlation id");
			}

			if (systemStatus.action == null) {
				throw new IllegalArgumentException("No action");
			}

			if (systemStatus.state == null) {
				throw new IllegalArgumentException("No state");
			}

			systemStatus.date = Date.from(Instant.now(Clock.systemDefaultZone()));
			return systemStatus;
		}
	}

	public static class ExchangeBuilder extends SystemStatus.Builder {

		private Exchange exchange;

		private ExchangeBuilder(Exchange exchange) {
			super();
			this.exchange = exchange;
			systemStatus.correlationId = exchange.getIn().getHeader(SYSTEM_STATUS_CORRELATION_ID, String.class);
			String actionString = exchange.getIn().getHeader(SYSTEM_STATUS_ACTION, String.class);
			if (actionString != null) {
				systemStatus.action = Action.valueOf(actionString);
			}
			systemStatus.entity = exchange.getIn().getHeader(SYSTEM_STATUS_ENTITY, String.class);
		}


		@Override
		public SystemStatus build() {
			if (exchange == null) {
				throw new IllegalStateException(this.getClass() + " does not hold an instance of exchange.");
			}

			SystemStatus status = super.build();
			exchange.getIn().setHeader(SYSTEM_STATUS_CORRELATION_ID, systemStatus.correlationId);
			exchange.getIn().setHeader(SYSTEM_STATUS_ACTION, systemStatus.action.toString());
			exchange.getIn().setHeader(SYSTEM_STATUS_ENTITY, systemStatus.entity);

			exchange.getOut().setBody(status.toString());
			exchange.getOut().setHeaders(exchange.getIn().getHeaders());
			return status;
		}
	}
}

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
	public enum System {KARTVERKET, GC, TIAMAT, PELIAS}

	public enum Entity {GRAPH, ADDRESS, ADMINISTRATIVE_UNITS, POI, PLACE_NAME, DELIVERY_PUBLICATION}

	public enum Action {FILE_TRANSFER, EXPORT, UPDATE, BUILD}

	public enum State {STARTED, TIMEOUT, FAILED, OK}

	@JsonProperty("correlation_id")
	private String correlationId;

	@JsonProperty("action")
	private SystemStatus.Action action;

	@JsonProperty("state")
	private SystemStatus.State state;

	@JsonProperty("jobType")
	private String jobType;

	@JsonProperty("entity")
	private String entity;

	@JsonProperty("source")
	private String source;

	@JsonProperty("target")
	private String target;

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


	public static SystemStatus fromString(String string) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			return mapper.readValue(string, SystemStatus.class);
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

	public String getJobType() {
		return jobType;
	}

	public String getSource() {
		return source;
	}

	public String getTarget() {
		return target;
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

		public SystemStatus.Builder start(Enum jobType) {
			return start(jobType.name());
		}

		public SystemStatus.Builder start(String jobType) {
			systemStatus = new SystemStatus();
			systemStatus.correlationId = UUID.randomUUID().toString();
			systemStatus.state = State.STARTED;
			return jobType(jobType);
		}

		public SystemStatus.Builder jobType(String jobType) {
			systemStatus.jobType = jobType;
			return this;
		}

		public SystemStatus.Builder entity(Entity entity) {
			systemStatus.entity = entity.toString();
			return this;
		}

		public SystemStatus.Builder source(System source) {
			systemStatus.source = source.toString();
			return this;
		}

		public SystemStatus.Builder target(System target) {
			systemStatus.target = target.toString();
			return this;
		}

		public SystemStatus.Builder correlationId(String correlationId) {
			systemStatus.correlationId = correlationId;
			return this;
		}

		public SystemStatus build() {
			if (systemStatus.jobType == null) {
				throw new IllegalArgumentException("No jobType");
			}

			if (systemStatus.correlationId == null) {
				throw new IllegalArgumentException("No correlation id");
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

			String currentStatusString = exchange.getIn().getHeader(SYSTEM_STATUS, String.class);

			if (currentStatusString != null) {
				SystemStatus currentStatus = SystemStatus.fromString(currentStatusString);
				systemStatus.correlationId = currentStatus.getCorrelationId();
				systemStatus.jobType = currentStatus.getJobType();
				systemStatus.action = currentStatus.getAction();
				systemStatus.source = currentStatus.getSource();
				systemStatus.target = currentStatus.getTarget();
				systemStatus.entity = currentStatus.getEntity();
			}

		}


		@Override
		public SystemStatus build() {
			if (exchange == null) {
				throw new IllegalStateException(this.getClass() + " does not hold an instance of exchange.");
			}

			SystemStatus status = super.build();
			exchange.getIn().setHeader(SYSTEM_STATUS, status.toString());

			exchange.getOut().setBody(status.toString());
			exchange.getOut().setHeaders(exchange.getIn().getHeaders());
			return status;
		}
	}
}

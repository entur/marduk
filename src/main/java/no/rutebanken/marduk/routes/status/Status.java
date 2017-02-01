package no.rutebanken.marduk.routes.status;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import no.rutebanken.marduk.Constants;
import org.apache.camel.Exchange;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

import static no.rutebanken.marduk.Constants.*;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Status {

	public enum Action {FILE_TRANSFER, FILE_CLASSIFICATION, IMPORT, EXPORT, VALIDATION_LEVEL_1, VALIDATION_LEVEL_2, CLEAN, DATASPACE_TRANSFER}

	public enum State {PENDING, STARTED, TIMEOUT, FAILED, OK, DUPLICATE}

	@JsonProperty("file_name")
	private String fileName;

	@JsonProperty("correlation_id")
	private String correlationId;

	@JsonProperty("provider_id")
	private Long providerId;


	@JsonProperty("job_id")
	private Long jobId;

	@JsonProperty("action")
	private Action action;

	@JsonProperty("state")
	private State state;

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS", timezone = "CET")
	@JsonProperty("date")
	private Date date;

	@JsonProperty("referential")
	private String referential;

	private Status() {
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

	public static Builder builder() {
		return new Builder();
	}

	public static Builder builder(Exchange exchange) {
		return new ExchangeBuilder(exchange);
	}

	public static class Builder {

		protected Status status = new Status();

		private Builder() {
		}

		public Builder action(Status.Action action) {
			status.action = action;
			return this;
		}

		public Builder state(Status.State state) {
			status.state = state;
			return this;
		}

		public Builder jobId(Long jobId) {
			status.jobId = jobId;
			return this;
		}

		public Builder fileName(String fileName) {
			status.fileName = fileName;
			return this;
		}

		public Builder providerId(Long providerId) {
			status.providerId = providerId;
			return this;
		}

		public Builder correlationId(String correlationId) {
			status.correlationId = correlationId;
			return this;
		}

		public Builder referential(String referential) {
			status.referential = referential;
			return this;
		}

		public Status build() {
			if (status.providerId == null) {
				throw new IllegalArgumentException("No provider id");
			}

			if (status.correlationId == null) {
				throw new IllegalArgumentException("No correlation id");
			}

			if (status.action == null) {
				throw new IllegalArgumentException("No action");
			}

			if (status.state == null) {
				throw new IllegalArgumentException("No state");
			}

			status.date = Date.from(Instant.now(Clock.systemDefaultZone()));
			return status;
		}
	}

	public static class ExchangeBuilder extends Builder {

		private Exchange exchange;

		private ExchangeBuilder(Exchange exchange) {
			super();
			this.exchange = exchange;
			status.fileName = exchange.getIn().getHeader(Constants.FILE_NAME, String.class);
			status.providerId = Long.valueOf(exchange.getIn().getHeader(Constants.ORIGINAL_PROVIDER_ID, exchange.getIn().getHeader(PROVIDER_ID, String.class), String.class));
			status.correlationId = exchange.getIn().getHeader(CORRELATION_ID, String.class);
			status.jobId = exchange.getIn().getHeader(CHOUETTE_JOB_ID, Long.class);
			status.referential = exchange.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class);
		}

		@Override
		public Status build() {
			if (exchange == null) {
				throw new IllegalStateException(this.getClass() + " does not hold an instance of exchange.");
			}

			Status status = super.build();
			exchange.getOut().setBody(status.toString());
			exchange.getOut().setHeaders(exchange.getIn().getHeaders());
			return status;
		}
	}

}

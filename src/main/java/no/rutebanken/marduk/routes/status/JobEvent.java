/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.routes.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import no.rutebanken.marduk.Constants;
import org.apache.camel.Exchange;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;
import java.util.UUID;

import static no.rutebanken.marduk.Constants.*;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobEvent {

    public enum JobDomain {TIMETABLE, GRAPH, TIMETABLE_PUBLISH}

    public enum TimetableAction {FILE_TRANSFER, FILE_CLASSIFICATION, IMPORT, EXPORT, VALIDATION_LEVEL_1, VALIDATION_LEVEL_2, CLEAN, DATASPACE_TRANSFER, BUILD_GRAPH, EXPORT_NETEX}

    public enum State {PENDING, STARTED, TIMEOUT, FAILED, OK, DUPLICATE, CANCELLED}

    public String name;

    public String correlationId;

    public Long providerId;

    public JobDomain domain;

    public Long externalId;

    public String action;

    public State state;

    public Instant eventTime;

    public String referential;

    private JobEvent() {
    }

    public String toString() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            StringWriter writer = new StringWriter();
            mapper.registerModule(new JavaTimeModule());
            mapper.writeValue(writer, this);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static JobEvent fromString(String string) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            return mapper.readValue(string, JobEvent.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static Builder builder() {
        return new Builder();
    }

    public static Builder providerJobBuilder(Exchange exchange) {
        return new ExchangeStatusBuilder(exchange).initProviderJob();
    }

    public static Builder systemJobBuilder(Exchange exchange) {
        return new ExchangeStatusBuilder(exchange).initSystemJob();
    }


    public static class Builder {

        protected JobEvent jobEvent = new JobEvent();

        private Builder() {
        }

        public Builder jobDomain(JobDomain jobDomain) {
            jobEvent.domain = jobDomain;
            return this;
        }

        public Builder timetableAction(TimetableAction action) {
            jobEvent.action = action.toString();
            jobDomain(JobDomain.TIMETABLE);
            return this;
        }

        public Builder action(String action) {
            jobEvent.action = action;
            return this;
        }

        public Builder state(JobEvent.State state) {
            jobEvent.state = state;
            return this;
        }

        public Builder jobId(Long jobId) {
            jobEvent.externalId = jobId;
            return this;
        }

        public Builder fileName(String fileName) {
            jobEvent.name = fileName;
            return this;
        }

        public Builder providerId(Long providerId) {
            jobEvent.providerId = providerId;
            return this;
        }

        public Builder newCorrelationId() {
            jobEvent.correlationId = UUID.randomUUID().toString();
            return this;
        }

        public Builder correlationId(String correlationId) {
            jobEvent.correlationId = correlationId;
            return this;
        }

        public Builder referential(String referential) {
            jobEvent.referential = referential;
            return this;
        }

        public JobEvent build() {
            if (JobDomain.TIMETABLE.equals(jobEvent.domain) && jobEvent.providerId == null) {
                throw new IllegalArgumentException("No provider id");
            }
            if (jobEvent.correlationId == null) {
                throw new IllegalArgumentException("No correlation id");
            }

            if (jobEvent.action == null) {
                throw new IllegalArgumentException("No action");
            }

            if (jobEvent.state == null) {
                throw new IllegalArgumentException("No state");
            }
            if (jobEvent.domain == null) {
                throw new IllegalArgumentException("No job domain");
            }
            jobEvent.eventTime = Instant.now();
            return jobEvent;
        }
    }

    public static class ExchangeStatusBuilder extends Builder {

        private Exchange exchange;

        private ExchangeStatusBuilder(Exchange exchange) {
            super();
            this.exchange = exchange;
        }

        private Builder initProviderJob() {
            jobEvent.name = exchange.getIn().getHeader(Constants.FILE_NAME, String.class);
            jobEvent.providerId = Long.valueOf(exchange.getIn().getHeader(Constants.ORIGINAL_PROVIDER_ID, exchange.getIn().getHeader(PROVIDER_ID, String.class), String.class));
            jobEvent.correlationId = exchange.getIn().getHeader(CORRELATION_ID, String.class);
            jobEvent.externalId = exchange.getIn().getHeader(CHOUETTE_JOB_ID, Long.class);
            jobEvent.referential = exchange.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class);
            return this;
        }

        private Builder initSystemJob() {

            String currentStatusString = exchange.getIn().getHeader(SYSTEM_STATUS, String.class);

            if (currentStatusString != null) {
                JobEvent currentJobEvent = JobEvent.fromString(currentStatusString);
                jobEvent.correlationId = currentJobEvent.correlationId;
                jobEvent.domain = currentJobEvent.domain;
                jobEvent.action = currentJobEvent.action;
                jobEvent.name = currentJobEvent.name;
                jobEvent.externalId = currentJobEvent.externalId;
            }
            return this;
        }

        @Override
        public JobEvent build() {
            if (exchange == null) {
                throw new IllegalStateException(this.getClass() + " does not hold an instance of exchange.");
            }

            JobEvent jobEvent = super.build();

            exchange.getIn().setHeader(SYSTEM_STATUS, jobEvent.toString());
            exchange.getOut().setBody(jobEvent.toString());
            exchange.getOut().setHeaders(exchange.getIn().getHeaders());
            return jobEvent;
        }
    }

}

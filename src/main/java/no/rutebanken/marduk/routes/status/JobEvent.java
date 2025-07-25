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
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.json.ObjectMapperFactory;
import org.apache.camel.Exchange;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static no.rutebanken.marduk.Constants.ANTU_VALIDATION_REPORT_ID;
import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_ID;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.JOB_ERROR_CODE;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.SYSTEM_STATUS;
import static no.rutebanken.marduk.Constants.USERNAME;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobEvent {


    /**
     * The file extension is neither .zip nor .ZIP.
     */
    public static final String JOB_ERROR_FILE_UNKNOWN_FILE_EXTENSION = "ERROR_FILE_UNKNOWN_FILE_EXTENSION";

    /**
     * The file is not a zip archive.
     */
    public static final String JOB_ERROR_FILE_NOT_A_ZIP_FILE = "ERROR_FILE_NOT_A_ZIP_FILE";

    /**
     * The file has already been imported.
     */
    public static final String JOB_ERROR_DUPLICATE_FILE = "ERROR_FILE_DUPLICATE";


    /**
     * The file is neither a NeTEx archive nor a GTFS archive.
     */
    public static final String JOB_ERROR_UNKNOWN_FILE_TYPE = "ERROR_FILE_UNKNOWN_FILE_TYPE";

    /**
     * The archive contains file names that are not UTF8-encoded.
     */
    public static final String JOB_ERROR_FILE_ZIP_CONTAINS_SUB_DIRECTORIES = "ERROR_FILE_ZIP_CONTAINS_SUB_DIRECTORIES";

    /**
     * The archive contains file names that are not UTF8-encoded.
     */
    public static final String JOB_ERROR_INVALID_ZIP_ENTRY_ENCODING = "ERROR_FILE_INVALID_ZIP_ENTRY_ENCODING";

    /**
     * The archive contains XML files with an invalid encoding.
     */
    public static final String JOB_ERROR_INVALID_XML_ENCODING = "ERROR_FILE_INVALID_XML_ENCODING_ERROR";

    /**
     * The archive contains invalid XML file.
     */
    public static final String JOB_ERROR_INVALID_XML_CONTENT = "ERROR_FILE_INVALID_XML_CONTENT";

    /**
     * The exported dataset is empty (no active timetable data found).
     */
    public static final String JOB_ERROR_NETEX_EXPORT_EMPTY = "ERROR_NETEX_EXPORT_EMPTY_EXPORT";

    /**
     * There is no data to be validated. Check the status of the latest data import.
     */
    public static final String JOB_ERROR_VALIDATION_NO_DATA = "ERROR_VALIDATION_NO_DATA";


    /**
     * Chouette failure codes
     */
    public static final String CHOUETTE_JOB_FAILURE_CODE_NO_DATA_PROCEEDED = "NO_DATA_PROCEEDED";
    public static final String CHOUETTE_JOB_FAILURE_CODE_NO_DATA_FOUND = "NO_DATA_FOUND";

    private static final ObjectWriter OBJECT_WRITER = ObjectMapperFactory.getSharedObjectMapper().writerFor(JobEvent.class);
    private static final ObjectReader OBJECT_READER = ObjectMapperFactory.getSharedObjectMapper().readerFor(JobEvent.class);

    public String getName() {
        return name;
    }

    public JobEvent setName(String name) {
        this.name = name;
        return this;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public JobEvent setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
        return this;
    }

    public Long getProviderId() {
        return providerId;
    }

    public JobEvent setProviderId(Long providerId) {
        this.providerId = providerId;
        return this;
    }

    public JobDomain getDomain() {
        return domain;
    }

    public JobEvent setDomain(JobDomain domain) {
        this.domain = domain;
        return this;
    }

    public String getExternalId() {
        return externalId;
    }

    public JobEvent setExternalId(String externalId) {
        this.externalId = externalId;
        return this;
    }

    public String getAction() {
        return action;
    }

    public JobEvent setAction(String action) {
        this.action = action;
        return this;
    }

    public State getState() {
        return state;
    }

    public JobEvent setState(State state) {
        this.state = state;
        return this;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public JobEvent setEventTime(Instant eventTime) {
        this.eventTime = eventTime;
        return this;
    }

    public String getReferential() {
        return referential;
    }

    public JobEvent setReferential(String referential) {
        this.referential = referential;
        return this;
    }

    public String getUsername() {
        return username;
    }

    public JobEvent setUsername(String username) {
        this.username = username;
        return this;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public JobEvent setErrorCode(String errorCode) {
        this.errorCode = errorCode;
        return this;
    }


    public enum JobDomain {TIMETABLE, GRAPH, TIMETABLE_PUBLISH}

    public enum TimetableAction {
        FILE_TRANSFER,
        FILE_CLASSIFICATION,
        PREVALIDATION,
        IMPORT,
        EXPORT,
        VALIDATION_LEVEL_1,
        VALIDATION_LEVEL_2,
        CLEAN,
        DATASPACE_TRANSFER,
        BUILD_GRAPH,
        BUILD_BASE,
        OTP2_BUILD_GRAPH,
        OTP2_BUILD_BASE,
        EXPORT_NETEX,
        EXPORT_NETEX_POSTVALIDATION,
        EXPORT_NETEX_MERGED,

        EXPORT_NETEX_MERGED_POSTVALIDATION,
        EXPORT_NETEX_BLOCKS,
        EXPORT_NETEX_BLOCKS_POSTVALIDATION,

        EXPORT_GTFS_MERGED,
        EXPORT_GTFS_BASIC_MERGED,

    }

    public enum State {PENDING, STARTED, TIMEOUT, FAILED, OK, DUPLICATE, CANCELLED}

    private String name;

    private String correlationId;

    private Long providerId;

    private JobDomain domain;

    private String externalId;

    private String action;

    private State state;

    private Instant eventTime;

    private String referential;

    private String username;

    private String errorCode;

    private JobEvent() {
    }

    public String toString() {
        try {
            return OBJECT_WRITER.writeValueAsString(this);
        } catch (IOException e) {
            throw new MardukException(e);
        }
    }


    public static JobEvent fromString(String string) {
        try {
            return OBJECT_READER.readValue(string);
        } catch (IOException e) {
            throw new MardukException(e);
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

        protected final JobEvent jobEvent = new JobEvent();

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

        public Builder action(TimetableAction action) {
            jobEvent.action = action.toString();
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

        public Builder jobId(String jobId) {
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

        public Builder username(String username) {
            jobEvent.username = username;
            return this;
        }

        public Builder errorCode(String errorCode) {
            jobEvent.errorCode = errorCode;
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

        private final Exchange exchange;

        private ExchangeStatusBuilder(Exchange exchange) {
            super();
            this.exchange = exchange;
        }

        private Builder initProviderJob() {
            jobEvent.name = exchange.getIn().getHeader(Constants.FILE_NAME, String.class);
            String providerId = exchange.getIn().getHeader(PROVIDER_ID, String.class);
            String originalProviderId = exchange.getIn().getHeader(Constants.ORIGINAL_PROVIDER_ID, providerId, String.class);
            if (originalProviderId == null) {
                throw new IllegalStateException("Neither the provider id nor the original provider id are defined in the current exchange");
            }
            jobEvent.providerId = Long.valueOf(originalProviderId);
            jobEvent.correlationId = exchange.getIn().getHeader(CORRELATION_ID, String.class);
            jobEvent.externalId = exchange.getIn().getHeader(CHOUETTE_JOB_ID, String.class);
            if(jobEvent.externalId == null) {
                jobEvent.externalId = exchange.getIn().getHeader(ANTU_VALIDATION_REPORT_ID, String.class);
            }
            jobEvent.referential = exchange.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class);
            if(jobEvent.referential == null) {
                jobEvent.referential = exchange.getIn().getHeader(DATASET_REFERENTIAL, String.class);
            }
            jobEvent.username = exchange.getIn().getHeader(USERNAME, String.class);
            jobEvent.errorCode = exchange.getIn().getHeader(JOB_ERROR_CODE, String.class);
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
                jobEvent.username = currentJobEvent.username;
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
            exchange.getMessage().setBody(jobEvent.toString());
            exchange.getMessage().setHeaders(exchange.getIn().getHeaders());
            return jobEvent;
        }
    }

}

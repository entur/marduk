package no.rutebanken.marduk.routes.status;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import org.apache.camel.Exchange;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

public class Status {

    /* Example

        {
          "status": {
            "file_name": "00011-gtfs.zip",
            "correlation_id": "123456789",
            "provider_id": "2",
            "action": "IMPORT",
            "state": "PENDING",
            "date": "2015-09-02T00:00:00Z",
            "status": "OK"
           }
        }

     */

    public enum Action {FILE_TRANSFER, IMPORT, EXPORT, VALIDATION}

    public enum State {PENDING, STARTED, TIMEOUT, FAILED, OK}

//    public enum Channel {SFTP, WEB}

    @JsonProperty("file_name")
    public String fileName;

    @JsonProperty("correlation_id")
    public String correlationId;

    @JsonProperty("provider_id")
    public Long providerId;

    @JsonProperty("action")
    public Action action;

    @JsonProperty("state")
    public State state;

//    @JsonProperty("channel")
//    public Channel channel;

    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd'T'HH:mm:ss", timezone="CET")
    @JsonProperty("date")
    public Date date;

    public Status(String fileName, Long providerId, Action action, State state, String correlationId) {
        this.fileName = fileName;
        this.providerId = providerId;
        this.action = action;
        this.state = state;
        this.correlationId = correlationId;
        this.date = Date.from( Instant.now(Clock.systemDefaultZone()));            //LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant());
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

    public static void addStatus(Exchange exchange, Status.Action action, Status.State state) {
        String fileName = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
        if (Strings.isNullOrEmpty(fileName)) {
            throw new IllegalArgumentException("No file name");
        }

        String providerIdString = exchange.getIn().getHeader(PROVIDER_ID, String.class);
        if (Strings.isNullOrEmpty(providerIdString)) {
            throw new IllegalArgumentException("No provider id");
        }
        Long providerId = Long.valueOf(providerIdString);

        String correlationId = exchange.getIn().getHeader(CORRELATION_ID, String.class);
        if (Strings.isNullOrEmpty(correlationId)) {
            throw new IllegalArgumentException("No correlation id");
        }

        exchange.getOut().setBody(new Status(fileName, providerId, action, state, correlationId).toString());
        exchange.getOut().setHeaders(exchange.getIn().getHeaders());
    }

}

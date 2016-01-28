package no.rutebanken.marduk.status;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

public class Status {

    /* Example

        {
          "status": {
            "file_name": "00011-gtfs.zip",
            "provider_id": "2",
            "action": "IMPORT",
            "state": "PENDING",
            "date": "2015-09-02T00:00:00Z",
            "status": "OK"
           }
        }

     */

    public enum Action {FILE_RECEIVED, IMPORT, EXPORT, VALIDATION}

    public enum State {PENDING, STARTED, FAILED, OK}

    @JsonProperty("file_name")
    public String fileName;

    @JsonProperty("provider_id")
    public String providerId;

    @JsonProperty("action")
    public Action action;

    @JsonProperty("state")
    public State state;

    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("date")
    public Date date;

    public Status(String fileName, String providerId, Action action, State state) {
        this.fileName = fileName;
        this.providerId = providerId;
        this.action = action;
        this.state = state;
        this.date = Date.from(Instant.now());            //LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant());
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

}

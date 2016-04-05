package no.rutebanken.marduk.routes.otp;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Date;

public class Metadata {

    public enum Action {OTP_GRAPH_UPLOAD}

    public enum Status {OK, NOK}

    public String description;

    @JsonProperty("file_name")
    public String fileName;

    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd'T'HH:mm:ss", timezone="CET")
    @JsonProperty("date")
    public Date date;

    public Status status;

    public Action action;

    public Metadata(String description, String fileName, Date date, Status status, Action action) {
        this.description = description;
        this.fileName = fileName;
        this.date = date;
        this.status = status;
        this.action = action;
    }

    String getJson() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(this);
    }
}

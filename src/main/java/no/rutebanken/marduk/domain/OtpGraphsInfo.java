package no.rutebanken.marduk.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.List;

public record OtpGraphsInfo(List<OtpGraphFile> streetGraphs, List<OtpGraphFile> transitGraphs) {

    public record OtpGraphFile(String name, String serializationId, @JsonFormat(without = JsonFormat.Feature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS) Instant creationDate, long size) {
    }
}

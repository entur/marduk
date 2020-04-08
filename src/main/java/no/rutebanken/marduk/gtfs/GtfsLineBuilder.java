package no.rutebanken.marduk.gtfs;

import no.rutebanken.marduk.exceptions.MardukException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class GtfsLineBuilder {

    private String[] sourceHeaders;
    private String[] targetHeaders;

    public GtfsLineBuilder(String[] sourceHeaders, String[] targetHeaders) {
        this.sourceHeaders = sourceHeaders;
        this.targetHeaders = targetHeaders;
    }

    public String getLine(String line) {
        String[] sourceValues = line.split(",", -1);
        Map<String, String> headerToValueMap = new HashMap<>(sourceHeaders.length);

        if (sourceHeaders.length != sourceValues.length) {
            throw new MardukException("CSV format error for line " + line);
        }

        for (int i = 0; i < sourceHeaders.length; i++) {
            headerToValueMap.put(sourceHeaders[i], sourceValues[i]);
        }

        StringBuilder sb = new StringBuilder(line.length());
        for (int i = 0; i < targetHeaders.length; i++) {
            sb.append(Optional.ofNullable(headerToValueMap.get(targetHeaders[i])).orElse(""));
            if (i < targetHeaders.length - 1) {
                sb.append(',');
            }
        }
        return sb.toString();
    }
}

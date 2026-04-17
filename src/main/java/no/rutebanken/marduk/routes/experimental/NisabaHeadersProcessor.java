package no.rutebanken.marduk.routes.experimental;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;

import static no.rutebanken.marduk.Constants.*;

/**
 * Processor used to set headers for files being uploaded to the Nisaba exchange bucket.
 * */
public class NisabaHeadersProcessor implements Processor {
    private static final Logger LOGGER = LoggerFactory.getLogger(NisabaHeadersProcessor.class);
    private final String containerName;

    public NisabaHeadersProcessor(String containerName) {
        this.containerName = containerName;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        DateTimeFormatter fmt = new DateTimeFormatterBuilder()
                .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
                .appendFraction(ChronoField.NANO_OF_SECOND, 0, 3, true)
                .toFormatter();
        LocalDateTime dateTime = resolveTimestamp(exchange);
        String timestamp = dateTime.format(fmt).replace(":", "_");
        String referential = exchange.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class);
        String targetFileName = "/" + referential + "_" + timestamp + ".zip";
        String pathToTargetFile = "imported/" + referential + targetFileName;
        exchange.getIn().setHeader(TARGET_FILE_HANDLE, pathToTargetFile);
        exchange.getIn().setHeader(TARGET_CONTAINER, containerName);
        LOGGER.info("Set Nisaba upload target: container='{}', fileHandle='{}'", containerName, pathToTargetFile);
    }

    private LocalDateTime resolveTimestamp(Exchange exchange) {
        String headerValue = exchange.getIn().getHeader(FILTERING_FILE_CREATED_TIMESTAMP, String.class);
        if (headerValue != null) {
            try {
                LocalDateTime parsed = LocalDateTime.parse(headerValue);
                LOGGER.info("Using timestamp from {} header: {}", FILTERING_FILE_CREATED_TIMESTAMP, parsed);
                return parsed;
            } catch (DateTimeParseException e) {
                LOGGER.warn("Failed to parse {} header value '{}', falling back to current time", FILTERING_FILE_CREATED_TIMESTAMP, headerValue, e);
            }
        } else {
            LOGGER.info("No {} header present, falling back to current time", FILTERING_FILE_CREATED_TIMESTAMP);
        }
        return LocalDateTime.now();
    }
}
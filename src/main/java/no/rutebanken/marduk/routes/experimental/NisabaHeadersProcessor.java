package no.rutebanken.marduk.routes.experimental;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

import static no.rutebanken.marduk.Constants.*;

/**
 * Processor used to set headers for files being uploaded to the Nisaba exchange bucket.
 * */
public class NisabaHeadersProcessor implements Processor {
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
        String timestamp = LocalDateTime.now().format(fmt).replace(":", "_");
        String targetFileName = "/" + exchange.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class) + "_" + timestamp + ".zip";
        String pathToTargetFile = "imported/" + exchange.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class) + targetFileName;
        exchange.getIn().setHeader(TARGET_FILE_HANDLE, pathToTargetFile);
        exchange.getIn().setHeader(TARGET_CONTAINER, containerName);
    }
}
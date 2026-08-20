package no.rutebanken.marduk.routes.experimental;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;

import static no.rutebanken.marduk.Constants.FILTERING_FILE_CREATED_TIMESTAMP;

/**
 * Works out where an original dataset is archived in the Nisaba exchange bucket.
 *
 * <p>The path is {@code imported/<referential>/<referential>_<timestamp>.zip}, with colons replaced by
 * underscores so it is a legal object name. Nisaba reads these by name, so the layout is a wire contract.
 */
public class NisabaHeadersProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(NisabaHeadersProcessor.class);

    private static final DateTimeFormatter TIMESTAMP = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 3, true)
            .toFormatter();

    /** Where to put the archived dataset. */
    public record UploadTarget(String fileHandle, String container) {
    }

    private final String containerName;

    public NisabaHeadersProcessor(String containerName) {
        this.containerName = containerName;
    }

    /**
     * @param createdTimestamp the value of the {@code FileCreatedTimestamp} header, or null; an unparseable
     *                         or absent value falls back to now, so the archive is still written
     */
    public UploadTarget targetFor(String referential, String createdTimestamp) {
        String timestamp = resolveTimestamp(createdTimestamp).format(TIMESTAMP).replace(":", "_");
        String pathToTargetFile = "imported/" + referential + "/" + referential + "_" + timestamp + ".zip";
        LOGGER.info("Set Nisaba upload target: container='{}', fileHandle='{}'", containerName, pathToTargetFile);
        return new UploadTarget(pathToTargetFile, containerName);
    }

    private static LocalDateTime resolveTimestamp(String createdTimestamp) {
        if (createdTimestamp == null) {
            LOGGER.info("No {} header present, falling back to current time", FILTERING_FILE_CREATED_TIMESTAMP);
            return LocalDateTime.now();
        }
        try {
            LocalDateTime parsed = LocalDateTime.parse(createdTimestamp);
            LOGGER.info("Using timestamp from {} header: {}", FILTERING_FILE_CREATED_TIMESTAMP, parsed);
            return parsed;
        } catch (DateTimeParseException e) {
            LOGGER.warn("Failed to parse {} header value '{}', falling back to current time",
                    FILTERING_FILE_CREATED_TIMESTAMP, createdTimestamp, e);
            return LocalDateTime.now();
        }
    }
}

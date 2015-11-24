package no.rutebanken.marduk.beans;

import com.google.common.collect.Sets;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class FileTypeClassifierBean {

    private static final Logger logger = LoggerFactory.getLogger(FileTypeClassifierBean.class);

    private static final String HEADER_FILETYPE = "filetype";
    private static final String FILETYPE_GTFS = "GTFS";
    private static final String FILETYPE_UNKNOWN = "UNKNOWN";

    private final String[] requiredGtfsFiles = {"agency.txt", "stops.txt", "routes.txt", "trips.txt", "stop_times.txt", "calendar.txt"};
    private final String[] optionalGtfsFiles = {"calendar_dates.txt", "fare_attributes.txt", "fare_rules.txt", "shapes.txt", "frequencies.txt", "transfers.txt", "feed_info.txt"};
    private final Set<String> requiredFiles = Arrays.stream(requiredGtfsFiles).collect(Collectors.toSet());
    private final Set<String> optionalFiles = Arrays.stream(optionalGtfsFiles).collect(Collectors.toSet());
    private final Set<String> possibleFiles = Sets.union(requiredFiles, optionalFiles);


    public boolean validateFile(Exchange exchange) {
        String relativePath = exchange.getIn().getHeader(Exchange.FILE_NAME_PRODUCED, String.class);
        Path path = Paths.get(relativePath);
        logger.debug("Validating '" + path);
        if (!Files.exists(path)) {
            logger.warn("File '" + path + "' does not exist.");
            return false;
        }

        File file = path.toFile();
        if (file.getName().endsWith(".zip")) {
            logger.debug("Ends with .zip. Checking whether this is a GTFS file.");
            if (isGtfsZip(file)) {
                logger.debug("File is a valid GTFS file.");
                exchange.getOut().setHeader(HEADER_FILETYPE, FILETYPE_GTFS);
                return true;
            }
            logger.info("File '" + path + "' was not a valid GTFS file.");
        }

        logger.info("Could not classify file '" + path + "'.");
        exchange.getOut().setHeader(HEADER_FILETYPE, FILETYPE_UNKNOWN);
        return false;
    }

    public boolean isGtfsZip(final File file) {
        final Set<String> filesInZip = getFilesInZip(file);

        Set<String> requiredFilesMissing = Sets.difference(requiredFiles, filesInZip);
        if (requiredFilesMissing.size() > 0) {
            logger.debug("Zip file is not a valid GTFS file, required files missing: " + requiredFilesMissing);
            return false;
        }

        Set<String> invalidFiles = Sets.difference(filesInZip, possibleFiles);
        if (invalidFiles.size() > 0) {
            logger.debug("There are invalid files in GTFS zip: " + invalidFiles);
            return false;    //TODO is this critical enough to fail validation?
        }

        return true;
    }

    private Set<String> getFilesInZip(File file) {
        try {
            final Enumeration<? extends ZipEntry> entries = new ZipFile(file).entries();
            final Set<String> filesInZip = new HashSet<>();
            while (entries.hasMoreElements()) {
                final ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    logger.debug("Got directory. Skipping.");
                    continue;
                }
                Path path = Paths.get(entry.getName());
                String fileName = path.getFileName().toString();
                logger.debug(fileName);
                filesInZip.add(fileName);
            }
            return filesInZip;
        } catch (IOException e) {
            logger.warn("Could not read file '" + file, e);
            return Collections.emptySet();
        }

    }

}

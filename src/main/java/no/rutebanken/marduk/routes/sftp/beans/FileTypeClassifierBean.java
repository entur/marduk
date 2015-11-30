package no.rutebanken.marduk.routes.sftp.beans;

import com.google.common.collect.Sets;
import no.rutebanken.marduk.exceptions.FileValidationException;
import no.rutebanken.marduk.routes.sftp.FileType;
import no.rutebanken.marduk.routes.sftp.ZipFileReader;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class FileTypeClassifierBean {

    private static final Logger logger = LoggerFactory.getLogger(FileTypeClassifierBean.class);

    private static final String HEADER_FILETYPE = "file_type";

    private final String[] requiredGtfsFiles = {"agency.txt", "stops.txt", "routes.txt", "trips.txt", "stop_times.txt", "calendar.txt"};
    private final String[] optionalGtfsFiles = {"calendar_dates.txt", "fare_attributes.txt", "fare_rules.txt", "shapes.txt", "frequencies.txt", "transfers.txt", "feed_info.txt"};
    private final Set<String> requiredFiles = Arrays.stream(requiredGtfsFiles).collect(Collectors.toSet());
    private final Set<String> optionalFiles = Arrays.stream(optionalGtfsFiles).collect(Collectors.toSet());
    private final Set<String> possibleFiles = Sets.union(requiredFiles, optionalFiles);
    private final ZipFileReader zipFileReader = new ZipFileReader();

    public boolean validateFile(Exchange exchange) {
        String relativePath = exchange.getIn().getHeader(Exchange.FILE_NAME_PRODUCED, String.class);
        try {
            Path path = Paths.get(relativePath);
            validatePath(path);
            if (path.toFile().getName().endsWith(".zip")) {
                logger.debug("FIle ends with .zip");
                if (isGtfsZip(zipFileReader.listFilesInZip(path.toFile()))){
                    exchange.getOut().setHeader(HEADER_FILETYPE, FileType.GTFS.name());
                    return true;
                }
            }
            throw new FileValidationException("Could not classify file '" + path + "'.");
        } catch (RuntimeException e){
            logger.warn("Failed while trying to classify file '" + relativePath + "'.", e);
            exchange.getOut().setHeader(HEADER_FILETYPE, FileType.INVALID.name());
            return false;
        }
    }

    private void validatePath(Path path) {
        logger.debug("Validating '" + path);
        if (!Files.exists(path)) {
            throw new FileValidationException("File '" + path + "' does not exist.");
        }
    }

    private boolean isGtfsZip(final Set<String> filesInZip) {
        logger.debug("Checking gtfs zip.");
        final Set<String> filesInZipTrimmed = trimLeadingPath(filesInZip);
        Set<String> requiredFilesMissing = Sets.difference(requiredFiles, filesInZipTrimmed);
        if (requiredFilesMissing.size() > 0) {
            throw new FileValidationException("Zip file is not a valid GTFS file, required files missing: " + requiredFilesMissing);
        }
        Set<String> invalidFiles = Sets.difference(filesInZipTrimmed, possibleFiles);
        if (invalidFiles.size() > 0) {
            throw new FileValidationException("There are invalid files in GTFS zip: " + invalidFiles);   //TODO is this critical enough to fail validation?
        }
        return true;
    }

    private Set<String> trimLeadingPath(Set<String> filesInZip) {
        return filesInZip.stream()
                .map(s -> Paths.get(s).getFileName().toString())
                .collect(Collectors.toSet());
    }

}

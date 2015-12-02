package no.rutebanken.marduk.routes.sftp.beans;

import com.google.common.collect.Sets;
import no.rutebanken.marduk.exceptions.FileValidationException;
import no.rutebanken.marduk.routes.sftp.FileType;
import no.rutebanken.marduk.routes.sftp.ZipFileReader;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public boolean validateFile(byte[] data, Exchange exchange) {
        String relativePath = exchange.getIn().getHeader("file_handle", String.class);
        logger.debug("Validating file with path '" + relativePath + "'.");
        if (relativePath == null || relativePath.trim().equals("")){
            throw new IllegalArgumentException("Could not get file path from file_handle header.");
        }
        try {
            if (relativePath.endsWith(".zip")) {
                logger.debug("FIle ends with .zip");
                if (isGtfsZip(zipFileReader.listFilesInZip(data))){
                    exchange.getOut().setHeader(HEADER_FILETYPE, FileType.GTFS.name());
                    return true;
                }
                throw new FileValidationException("Could not classify file '" + relativePath + "'.");
            }
            throw new FileValidationException("Could not classify file '" + relativePath + "'.");
        } catch (RuntimeException e) {
            logger.warn("Failed while trying to classify file '" + relativePath + "'.", e);
            return false;
        }
    }

    private boolean isGtfsZip(final Set<String> filesInZip) {
        logger.debug("Checking gtfs zip.");
        Set<String> requiredFilesMissing = Sets.difference(requiredFiles, filesInZip);
        if (requiredFilesMissing.size() > 0) {
            throw new FileValidationException("Zip file is not a valid GTFS file, required files missing: " + requiredFilesMissing);
        }
        Set<String> invalidFiles = Sets.difference(filesInZip, possibleFiles);
        if (invalidFiles.size() > 0) {
            throw new FileValidationException("There are invalid files in GTFS zip: " + invalidFiles);   //TODO is this critical enough to fail validation?
        }
        return true;
    }

}

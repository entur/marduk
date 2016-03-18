package no.rutebanken.marduk.routes.file.beans;

import com.google.common.collect.Sets;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.exceptions.FileValidationException;
import no.rutebanken.marduk.routes.file.FileType;
import no.rutebanken.marduk.routes.file.ZipFileReader;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class FileTypeClassifierBean {

    private static final Logger logger = LoggerFactory.getLogger(FileTypeClassifierBean.class);

    private final String[] requiredGtfsFiles = {"agency.txt", "stops.txt", "routes.txt", "trips.txt", "stop_times.txt", "calendar.txt"};
    private final String[] optionalGtfsFiles = {"calendar_dates.txt", "fare_attributes.txt", "fare_rules.txt", "shapes.txt", "frequencies.txt", "transfers.txt", "feed_info.txt"};  //TODO pathways.txt ?
    private final Set<String> requiredFiles = Arrays.stream(requiredGtfsFiles).collect(Collectors.toSet());
    private final Set<String> optionalFiles = Arrays.stream(optionalGtfsFiles).collect(Collectors.toSet());
    private final Set<String> possibleFiles = Sets.union(requiredFiles, optionalFiles);
    private final ZipFileReader zipFileReader = new ZipFileReader();

    public boolean validateFile(byte[] data, Exchange exchange) {
        String relativePath = exchange.getIn().getHeader(Constants.FILE_HANDLE, String.class);
        logger.debug("Validating file with path '" + relativePath + "'.");
        if (relativePath == null || relativePath.trim().equals("")){
            throw new IllegalArgumentException("Could not get file path from file_handle header.");
        }
        try {
            if (relativePath.endsWith(".zip")) {
                logger.debug("FIle ends with .zip");
                if (isGtfsZip(zipFileReader.listFilesInZip(data))){
                    exchange.getOut().setHeader(Constants.FILE_TYPE, FileType.GTFS.name());
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
            logger.warn("Zip file is not a valid GTFS file, required files missing: " + requiredFilesMissing);
//            throw new FileValidationException("Zip file is not a valid GTFS file, required files missing: " + requiredFilesMissing);  //TODO Add this back in when done with development?
        }
        Set<String> invalidFiles = Sets.difference(filesInZip, possibleFiles);
        if (invalidFiles.size() > 0) {
            logger.warn("Zip file is not a valid GTFS file, there are invalid files in GTFS zip:: " + invalidFiles);
//            throw new FileValidationException("There are invalid files in GTFS zip: " + invalidFiles);   //TODO is this critical enough to fail validation?
        }
        return true;
    }

}

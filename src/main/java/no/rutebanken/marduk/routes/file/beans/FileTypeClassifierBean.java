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

    private final String requiredRegtoppFilesExtensionsRegex = "(?i).+\\.tix";
    private final String requiredGtfsFilesRegex = "agency.txt|stops.txt|routes.txt|trips.txt|stop_times.txt";

    private final ZipFileReader zipFileReader = new ZipFileReader();

    public boolean validateFile(byte[] data, Exchange exchange) {
        String relativePath = exchange.getIn().getHeader(Constants.FILE_HANDLE, String.class);
        logger.debug("Validating file with path '" + relativePath + "'.");
        if (relativePath == null || relativePath.trim().equals("")){
            throw new IllegalArgumentException("Could not get file path from file_handle header.");
        }
        try {
            if (relativePath.endsWith(".zip")) {
                logger.debug("File ends with .zip");
                if (isRegtoppZip(zipFileReader.listFilesInZip(data))){
                    exchange.getIn().setHeader(Constants.FILE_TYPE, FileType.REGTOPP.name());
                    logger.debug("This is a regtopp zip.");
                    return true;
                }
                if (isGtfsZip(zipFileReader.listFilesInZip(data))){
                    exchange.getIn().setHeader(Constants.FILE_TYPE, FileType.GTFS.name());
                    logger.debug("This is a gtfs zip.");
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

    boolean isRegtoppZip(Set<String> filesInZip) {
        return filesInZip.stream().anyMatch(p -> p.matches(requiredRegtoppFilesExtensionsRegex));
    }

    boolean isGtfsZip(final Set<String> filesInZip) {
        return filesInZip.stream().anyMatch(p -> p.matches(requiredGtfsFilesRegex));
    }

}

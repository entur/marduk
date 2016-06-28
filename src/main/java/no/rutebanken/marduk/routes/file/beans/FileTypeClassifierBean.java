package no.rutebanken.marduk.routes.file.beans;

import java.util.Set;

import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.exceptions.FileValidationException;
import no.rutebanken.marduk.routes.file.FileType;
import no.rutebanken.marduk.routes.file.ZipFileReader;

public class FileTypeClassifierBean {

    private static final Logger logger = LoggerFactory.getLogger(FileTypeClassifierBean.class);

    private static final String requiredRegtoppFilesExtensionsRegex = "(?i).+\\.tix|(?i).+\\.hpl|(?i).+\\.dko";
    private static final String requiredGtfsFilesRegex = "agency.txt|stops.txt|routes.txt|trips.txt|stop_times.txt";

    private final ZipFileReader zipFileReader = new ZipFileReader();

    public boolean validateFile(byte[] data, Exchange exchange) {
        String relativePath = exchange.getIn().getHeader(Constants.FILE_HANDLE, String.class);
        logger.debug("Validating file with path '" + relativePath + "'.");
        if (relativePath == null || relativePath.trim().equals("")){
            throw new IllegalArgumentException("Could not get file path from file_handle header.");
        }
        try {
            if (relativePath.toUpperCase().endsWith(".ZIP")) {
                logger.debug("File ends with .zip");
                if (isRegtoppZip(zipFileReader.listFilesInZip(data))){
                    exchange.getIn().setHeader(Constants.FILE_TYPE, FileType.REGTOPP.name());
                    return true;
                }
                if (isGtfsZip(zipFileReader.listFilesInZip(data))){
                    exchange.getIn().setHeader(Constants.FILE_TYPE, FileType.GTFS.name());
                    logger.debug("This is a gtfs zip.");
                    return true;
                }
                throw new FileValidationException("Could not classify file '" + relativePath + "'.");
            } else if(relativePath.toUpperCase().endsWith(".RAR")) {
            	
                exchange.getIn().setHeader(Constants.FILE_TYPE, FileType.RAR.name());
                logger.debug("This is a rar file.");
                return true;
            }
            throw new FileValidationException("Could not classify file '" + relativePath + "'.");
        } catch (RuntimeException e) {
            logger.warn("Failed while trying to classify file '" + relativePath + "'.", e);
            return false;
        }
    }

    public static boolean  isRegtoppZip(Set<String> filesInZip) {
        return filesInZip.stream().anyMatch(p -> p.matches(requiredRegtoppFilesExtensionsRegex));
    }

    public static boolean isGtfsZip(final Set<String> filesInZip) {
        return filesInZip.stream().anyMatch(p -> p.matches(requiredGtfsFilesRegex));
    }

}

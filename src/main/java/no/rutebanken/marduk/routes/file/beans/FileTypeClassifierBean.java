package no.rutebanken.marduk.routes.file.beans;

import no.rutebanken.marduk.exceptions.FileValidationException;
import no.rutebanken.marduk.routes.file.FileType;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import org.apache.camel.Exchange;
import org.apache.commons.codec.CharEncoding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Set;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.FILE_TYPE;
import static no.rutebanken.marduk.routes.file.FileType.*;
import static no.rutebanken.marduk.routes.file.beans.FileClassifierPredicates.firstElementQNameMatchesNetex;
import static no.rutebanken.marduk.routes.file.beans.FileClassifierPredicates.validateZipContent;

public class FileTypeClassifierBean {

    private static final Logger logger = LoggerFactory.getLogger(FileTypeClassifierBean.class);

    private static final String requiredRegtoppFilesExtensionsRegex = "(?i).+\\.tix|(?i).+\\.hpl|(?i).+\\.dko";
    private static final String requiredGtfsFilesRegex = "agency.txt|stops.txt|routes.txt|trips.txt|stop_times.txt";
    private static final String requiredNeptuneFilesRegex = "[A-Z]{1,}\\-Line\\-[0-9].*\\.xml";
    private static final String xmlFilesRegex = ".+\\.xml";    //TODO can we be more specific?

    private final static ZipFileUtils zipFileUtils = new ZipFileUtils();

    public boolean validateFile(byte[] data, Exchange exchange) {
        String relativePath = exchange.getIn().getHeader(FILE_HANDLE, String.class);
        logger.debug("Validating file with path '" + relativePath + "'.");
        try {
            if (relativePath == null || relativePath.trim().equals("")) {
                throw new IllegalArgumentException("Could not get file path from " + FILE_HANDLE + " header.");
            }

            FileType fileType = classifyFile(relativePath, data);
            logger.debug("File is classified as " + fileType);
            exchange.getIn().setHeader(FILE_TYPE, fileType.name());
            return true;
        } catch (RuntimeException e) {
            logger.warn("Exception while trying to classify file '" + relativePath + "'", e);
            return false;
        }
    }

    boolean isValidFileName(String fileName) {
        return Charset.forName(CharEncoding.ISO_8859_1).newEncoder().canEncode(fileName);
    }

    public FileType classifyFile(String relativePath, byte[] data) {
        if (relativePath.toUpperCase().endsWith(".ZIP")) {
            Set<String> filesNamesInZip = zipFileUtils.listFilesInZip(new ByteArrayInputStream(data));
            if (!isValidFileName(relativePath)) {
                return INVALID_FILE_NAME;
            } else if (isRegtoppZip(filesNamesInZip)) {
                return REGTOPP;
            } else if (isGtfsZip(filesNamesInZip)) {
                return GTFS;
            } else if (isNetexZip(filesNamesInZip, new ByteArrayInputStream(data))) {
                return NETEXPROFILE;
            } else if (isNeptuneZip(filesNamesInZip)) {
                return NEPTUNE;
            } else if (ZipFileUtils.zipFileContainsSingleFolder(data)) {
                return ZIP_WITH_SINGLE_FOLDER;
            }
            throw new FileValidationException("Could not classify zip file '" + relativePath + "'.");
        } else if (relativePath.toUpperCase().endsWith(".RAR")) {
            return RAR;
        }
        throw new FileValidationException("Could not classify file '" + relativePath + "'.");
    }

    public static boolean isRegtoppZip(Set<String> filesInZip) {
        return filesInZip.stream().anyMatch(p -> p.matches(requiredRegtoppFilesExtensionsRegex));
    }

    public static boolean isGtfsZip(final Set<String> filesInZip) {
        return filesInZip.stream().anyMatch(p -> p.matches(requiredGtfsFilesRegex));
    }

    public static boolean isNeptuneZip(final Set<String> filesInZip) {
        return filesInZip.stream().anyMatch(p -> p.matches(requiredNeptuneFilesRegex));
    }

    public static boolean isNetexZip(final Set<String> filesInZip, InputStream inputStream) {
        return filesInZip.stream().anyMatch(p -> p.matches(xmlFilesRegex)) //TODO skip file extension check unless it can be more specific?
                       && isNetexXml(inputStream);
    }

    private static boolean isNetexXml(InputStream inputStream) {
        try {
            return validateZipContent(inputStream, firstElementQNameMatchesNetex());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}

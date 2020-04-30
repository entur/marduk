/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.routes.file.beans;

import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.exceptions.MardukZipFileEntryContentEncodingException;
import no.rutebanken.marduk.exceptions.MardukZipFileEntryNameEncodingException;
import no.rutebanken.marduk.routes.file.FileType;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_TYPE;
import static no.rutebanken.marduk.routes.file.FileType.GTFS;
import static no.rutebanken.marduk.routes.file.FileType.INVALID_FILE_NAME;
import static no.rutebanken.marduk.routes.file.FileType.INVALID_ZIP_FILE_ENTRY_CONTENT_ENCODING;
import static no.rutebanken.marduk.routes.file.FileType.INVALID_ZIP_FILE_ENTRY_NAME_ENCODING;
import static no.rutebanken.marduk.routes.file.FileType.NETEXPROFILE;
import static no.rutebanken.marduk.routes.file.FileType.NOT_A_ZIP_FILE;
import static no.rutebanken.marduk.routes.file.FileType.UNKNOWN_FILE_EXTENSION;
import static no.rutebanken.marduk.routes.file.FileType.UNKNOWN_FILE_TYPE;
import static no.rutebanken.marduk.routes.file.FileType.ZIP_WITH_SINGLE_FOLDER;
import static no.rutebanken.marduk.routes.file.beans.FileClassifierPredicates.firstElementQNameMatchesNetex;
import static no.rutebanken.marduk.routes.file.beans.FileClassifierPredicates.validateZipContent;

public class FileTypeClassifierBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileTypeClassifierBean.class);

    private static final String REQUIRED_GTFS_FILES_REGEX = "agency.txt|stops.txt|routes.txt|trips.txt|stop_times.txt";
    private static final String XML_FILES_REGEX = ".+\\.xml";    //TODO can we be more specific?

    public static final String NON_XML_FILE_XML=".*\\.(?!XML$|xml$)[^.]+";

    public boolean validateFile(byte[] data, Exchange exchange) {
        String relativePath = exchange.getIn().getHeader(FILE_HANDLE, String.class);
        LOGGER.debug("Validating file with path '{}'.", relativePath);
        try {
            if (relativePath == null || relativePath.isBlank()) {
                throw new IllegalArgumentException("Could not get file path from " + FILE_HANDLE + " header.");
            }

            FileType fileType = classifyFile(relativePath, data);
            LOGGER.debug("File is classified as {}", fileType);
            exchange.getIn().setHeader(FILE_TYPE, fileType.name());
            return true;
        } catch (RuntimeException e) {
            LOGGER.warn("Exception while trying to classify file '" + relativePath + "'", e);
            return false;
        }
    }

    boolean isValidFileName(String fileName) {
        return StandardCharsets.ISO_8859_1.newEncoder().canEncode(fileName);
    }

    public FileType classifyFile(String relativePath, byte[] data) {
        try {
            if (relativePath.toUpperCase().endsWith(".ZIP")) {
                Set<String> filesNamesInZip = ZipFileUtils.listFilesInZip(data);
                if(!isZipFile(data)) {
                    return NOT_A_ZIP_FILE;
                } else if (!isValidFileName(relativePath)) {
                    return INVALID_FILE_NAME;
                } else if (isGtfsZip(filesNamesInZip)) {
                    return GTFS;
                } else if (isNetexZip(filesNamesInZip, new ByteArrayInputStream(data))) {
                    return NETEXPROFILE;
                } else if (ZipFileUtils.zipFileContainsSingleFolder(data)) {
                    return ZIP_WITH_SINGLE_FOLDER;
                } else {
                    return UNKNOWN_FILE_TYPE;
                }
            } else {
                return UNKNOWN_FILE_EXTENSION;
            }
        } catch (MardukZipFileEntryNameEncodingException e) {
            LOGGER.info("Found a zip file entry name with an invalid encoding while classifying file " + relativePath, e);
            return INVALID_ZIP_FILE_ENTRY_NAME_ENCODING;
        } catch (MardukZipFileEntryContentEncodingException e) {
            LOGGER.info("Found a zip file entry with an invalid XML encoding while classifying file " + relativePath, e);
            return INVALID_ZIP_FILE_ENTRY_CONTENT_ENCODING;
        } catch (IOException e) {
            throw new MardukException("Exception while classifying file" + relativePath, e);
        }

}


    public static boolean isZipFile(byte[]data) {
        if(data.length < 4) {
            return false;
        }
        try(DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));) {
            return in.readInt() == 0x504b0304;
        } catch (IOException e) {
            return false;
        }
    }



    public static boolean isGtfsZip(final Set<String> filesInZip) {
        return filesInZip.stream().anyMatch(p -> p.matches(REQUIRED_GTFS_FILES_REGEX));
    }

    public static boolean isNetexZip(final Set<String> filesInZip, InputStream inputStream) throws MalformedInputException {
        return filesInZip.stream().anyMatch(p -> p.matches(XML_FILES_REGEX)) //TODO skip file extension check unless it can be more specific?
                       && isNetexXml(inputStream);
    }

    private static boolean isNetexXml(InputStream inputStream) throws MalformedInputException {
            return validateZipContent(inputStream, firstElementQNameMatchesNetex(), NON_XML_FILE_XML);
    }

}

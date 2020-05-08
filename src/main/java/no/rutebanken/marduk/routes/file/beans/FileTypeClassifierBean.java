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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

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
import static no.rutebanken.marduk.routes.file.FileType.ZIP_CONTAINS_SUBDIRECTORIES;
import static no.rutebanken.marduk.routes.file.beans.FileClassifierPredicates.firstElementQNameMatchesNetex;
import static no.rutebanken.marduk.routes.file.beans.FileClassifierPredicates.validateZipContent;

public class FileTypeClassifierBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileTypeClassifierBean.class);

    private static final Pattern REQUIRED_GTFS_FILES_REGEX = Pattern.compile("agency.txt|stops.txt|routes.txt|trips.txt|stop_times.txt");
    private static final Pattern XML_FILES_REGEX = Pattern.compile(".+\\.xml");

    public static final Pattern NON_XML_FILE_XML = Pattern.compile(".*\\.(?!XML$|xml$)[^.]+");

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

    public FileType classifyFile(String relativePath, byte[] data) {
        try {
            if (!relativePath.toUpperCase().endsWith(".ZIP")) {
                return UNKNOWN_FILE_EXTENSION;
            }
            if (!ZipFileUtils.isZipFile(data)) {
                return NOT_A_ZIP_FILE;
            }
            if (!isValidFileName(relativePath)) {
                return INVALID_FILE_NAME;
            }

            Set<ZipEntry> zipEntriesInZip = ZipFileUtils.listFilesInZip(data);

            if (containsDirectory(zipEntriesInZip)) {
                return ZIP_CONTAINS_SUBDIRECTORIES;
            }

            if (isGtfsZip(zipEntriesInZip)) {
                return GTFS;
            }
            if (isNetexZip(zipEntriesInZip, new ByteArrayInputStream(data))) {
                return NETEXPROFILE;
            }
            return UNKNOWN_FILE_TYPE;
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


    private static boolean isValidFileName(String fileName) {
        return StandardCharsets.ISO_8859_1.newEncoder().canEncode(fileName);
    }

    private static boolean containsDirectory(Set<ZipEntry> zipEntriesInZip) {
        return zipEntriesInZip.stream().anyMatch(ze -> ze.isDirectory());
    }

    private static boolean isGtfsZip(final Set<ZipEntry> zipEntriesInZip) {
        return zipEntriesInZip.stream().anyMatch(ze -> REQUIRED_GTFS_FILES_REGEX.matcher(ze.getName()).matches());
    }

    private static boolean isNetexZip(final Set<ZipEntry> zipEntriesInZip, InputStream inputStream) {
        return zipEntriesInZip.stream().anyMatch(ze -> XML_FILES_REGEX.matcher(ze.getName()).matches())
                && isNetexXml(inputStream);
    }

    private static boolean isNetexXml(InputStream inputStream) {
        return validateZipContent(inputStream, firstElementQNameMatchesNetex(), NON_XML_FILE_XML);
    }

}

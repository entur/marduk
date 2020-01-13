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

import no.rutebanken.marduk.exceptions.FileValidationException;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.routes.file.FileType;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import org.apache.camel.Exchange;
import org.apache.commons.codec.CharEncoding;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Set;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_TYPE;
import static no.rutebanken.marduk.routes.file.FileType.GTFS;
import static no.rutebanken.marduk.routes.file.FileType.INVALID_FILE_NAME;
import static no.rutebanken.marduk.routes.file.FileType.NEPTUNE;
import static no.rutebanken.marduk.routes.file.FileType.NETEXPROFILE;
import static no.rutebanken.marduk.routes.file.FileType.REGTOPP;
import static no.rutebanken.marduk.routes.file.FileType.ZIP_WITH_SINGLE_FOLDER;
import static no.rutebanken.marduk.routes.file.beans.FileClassifierPredicates.firstElementQNameMatchesNetex;
import static no.rutebanken.marduk.routes.file.beans.FileClassifierPredicates.validateZipContent;

public class FileTypeClassifierBean {

    private static final Logger logger = LoggerFactory.getLogger(FileTypeClassifierBean.class);

    private static final String requiredRegtoppFilesExtensionsRegex = "(?i).+\\.tix|(?i).+\\.hpl|(?i).+\\.dko";
    private static final String requiredGtfsFilesRegex = "agency.txt|stops.txt|routes.txt|trips.txt|stop_times.txt";
    private static final String xmlFilesRegex = ".+\\.xml";    //TODO can we be more specific?

    public static final String NON_XML_FILE_XML=".*\\.(?!XML$|xml$)[^.]+";

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
        try {
            if (relativePath.toUpperCase().endsWith(".ZIP")) {
                Set<String> filesNamesInZip = ZipFileUtils.listFilesInZip(data);
                if (!isValidFileName(relativePath)) {
                    return INVALID_FILE_NAME;
                } else if (isRegtoppZip(filesNamesInZip)) {
                    return REGTOPP;
                } else if (isGtfsZip(filesNamesInZip)) {
                    return GTFS;
                } else if (isNetexZip(filesNamesInZip, new ByteArrayInputStream(data))) {
                    return NETEXPROFILE;
                } else if (ZipFileUtils.zipFileContainsSingleFolder(data)) {
                    return ZIP_WITH_SINGLE_FOLDER;
                }
                throw new FileValidationException("Could not classify zip file '" + relativePath + "'.");
            }
            throw new FileValidationException("Could not classify file '" + relativePath + "'.");
        } catch (IOException e) {
            throw new MardukException("Exception while classifying file", e);
        }
    }

    public static boolean isRegtoppZip(Set<String> filesInZip) {
        return filesInZip.stream().anyMatch(p -> p.matches(requiredRegtoppFilesExtensionsRegex));
    }

    public static boolean isGtfsZip(final Set<String> filesInZip) {
        return filesInZip.stream().anyMatch(p -> p.matches(requiredGtfsFilesRegex));
    }

    public static boolean isNetexZip(final Set<String> filesInZip, InputStream inputStream) {
        return filesInZip.stream().anyMatch(p -> p.matches(xmlFilesRegex)) //TODO skip file extension check unless it can be more specific?
                       && isNetexXml(inputStream);
    }

    private static boolean isNetexXml(InputStream inputStream) {
        try {
            return validateZipContent(inputStream, firstElementQNameMatchesNetex(), NON_XML_FILE_XML);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}

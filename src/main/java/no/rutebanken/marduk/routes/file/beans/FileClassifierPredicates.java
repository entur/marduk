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
import no.rutebanken.marduk.exceptions.MardukZipFileEntryContentEncodingException;
import no.rutebanken.marduk.exceptions.MardukZipFileEntryContentParsingException;
import no.rutebanken.marduk.routes.file.MardukFileUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StreamUtils;
import org.springframework.util.xml.StaxUtils;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.CharConversionException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileClassifierPredicates {

    public static final QName NETEX_PUBLICATION_DELIVERY_QNAME = new QName("http://www.netex.org.uk/netex", "PublicationDelivery");

    private static final XMLInputFactory xmlInputFactory = StaxUtils.createDefensiveInputFactory();

    private static final Logger LOGGER = LoggerFactory.getLogger(FileClassifierPredicates.class);

    private FileClassifierPredicates() {
    }

    public static Predicate<InputStream> firstElementQNameMatchesNetex() {
        return inputStream -> firstElementQNameMatches(NETEX_PUBLICATION_DELIVERY_QNAME).test(inputStream);
    }

    public static Predicate<InputStream> firstElementQNameMatches(QName qName) {
        return inputStream -> getFirstElementQName(inputStream)
                .orElseThrow(FileValidationException::new)
                .equals(qName);
    }

    private static Optional<QName> getFirstElementQName(InputStream inputStream) {
        XMLStreamReader streamReader = null;
        try {
            streamReader = xmlInputFactory.createXMLStreamReader(inputStream);
            while (streamReader.hasNext()) {
                int eventType = streamReader.next();
                if (eventType == XMLStreamConstants.START_ELEMENT) {
                    return Optional.of(streamReader.getName());
                } else if (eventType != XMLStreamConstants.COMMENT) {
                    // If event is neither start of element or a comment, then this is probably not a xml file.
                    break;
                }
            }
        } catch (XMLStreamException e) {
            Throwable rootCause = ExceptionUtils.getRootCause(e);
            if (rootCause instanceof CharConversionException) {
                throw new MardukZipFileEntryContentEncodingException(e);
            } else {
                throw new MardukZipFileEntryContentParsingException(e);
            }
        } finally {
            try {
                if (streamReader != null) {
                    streamReader.close();
                }
            } catch (XMLStreamException e) {
                LOGGER.warn("Exception while closing the stream reader", e);
            }
        }
        return Optional.empty();
    }

    /**
     * Check that files in the zip archive verify the predicate.
     *
     * @param inputStream     the zip file.
     * @param predicate       the predicate to evaluate.
     * @param fileNamePattern the pattern for file names to be tested. Other files are ignored.
     * @return true if all tested files verify the predicate.
     */
    public static boolean validateZipContent(InputStream inputStream, Predicate<InputStream> predicate, Pattern fileNamePattern) {
        try (ZipInputStream stream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = stream.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (fileNamePattern.matcher(entryName).matches()) {
                    if (testPredicate(predicate, stream, entry)) {
                        return false;
                    }
                } else {
                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info("Skipped zip entry with name {}", MardukFileUtils.sanitizeFileName(entryName));
                    }
                }
            }
            return true;
        } catch (IOException e) {
            throw new MardukException(e);
        }
    }

    private static boolean testPredicate(Predicate<InputStream> predicate, ZipInputStream stream, ZipEntry entry) {
        String entryName = entry.getName();
        try {
            if (!predicate.test(StreamUtils.nonClosing(stream))) {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("Zip entry {} with size {} is invalid.", MardukFileUtils.sanitizeFileName(entryName), entry.getSize());
                }
                return true;
            }
        } catch (MardukZipFileEntryContentEncodingException e) {
            throw new MardukZipFileEntryContentEncodingException("Encoding exception while trying to classify zip file entry " + entryName, e);
        } catch (MardukZipFileEntryContentParsingException e) {
            throw new MardukZipFileEntryContentParsingException("Parsing exception while trying to classify zip file entry " + entryName, e);
        } catch (Exception e) {
            throw new MardukException("Exception while trying to classify zip file entry " + entryName, e);
        }
        return false;
    }
}

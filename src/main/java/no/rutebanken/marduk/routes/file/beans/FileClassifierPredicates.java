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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileClassifierPredicates {

    public static final QName NETEX_PUBLICATION_DELIVERY_QNAME = new QName("http://www.netex.org.uk/netex", "PublicationDelivery");

    private static XMLInputFactory xmlInputFactory = XMLInputFactory.newFactory();

    private static final Logger logger = LoggerFactory.getLogger(FileClassifierPredicates.class);

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
                if (eventType == XMLStreamReader.START_ELEMENT) {
                    return Optional.of(streamReader.getName());
                } else if (eventType != XMLStreamReader.COMMENT) {
                    // If event is neither start of element or a comment, then this is probably not a xml file.
                    break;
                }
            }
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                streamReader.close();
            } catch (XMLStreamException e) {
                throw new RuntimeException("Exception while closing", e);
            }
        }
        return Optional.empty();
    }

    public static boolean validateZipContent(InputStream inputStream, Predicate<InputStream> predicate) {
        try (ZipInputStream stream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ( ( entry = stream.getNextEntry()) != null && !entry.isDirectory()) {
                if (testPredicate(predicate, stream, entry)) return false;
            }
            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static boolean validateZipContent(InputStream inputStream, Predicate<InputStream> predicate, String skipFileRegex) {
        try (ZipInputStream stream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ( ( entry = stream.getNextEntry()) != null) {
                if (!entry.getName().matches(skipFileRegex)) {
                    if (testPredicate(predicate, stream, entry)) return false;
                } else {
                    logger.info("Skipped file with name " + entry.getName());
                }
            }
            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean testPredicate(Predicate<InputStream> predicate, ZipInputStream stream, ZipEntry entry) {
    	try {
			if (!predicate.test(new CloseIgnoringInputStream(stream))) {
			    String s = String.format("Entry %s with size %d is invalid.",
			            entry.getName(), entry.getSize(),
			            new Date(entry.getTime()));
			    logger.info(s);
			    return true;
			}
		} catch (Exception e) {
			throw new RuntimeException("Exception while trying to classify file "+entry.getName()+" in zip file", e);
		}
        return false;
    }
}


class CloseIgnoringInputStream extends InputStream {

    private ZipInputStream stream;

    public CloseIgnoringInputStream(ZipInputStream inStream) {
        stream = inStream;
    }

    public int read() throws IOException {
        return stream.read();
    }

    public void close() {
        //ignore
    }

    public void reallyClose() throws IOException {
        stream.close();
    }
}
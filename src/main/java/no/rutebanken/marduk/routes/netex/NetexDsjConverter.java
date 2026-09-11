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

package no.rutebanken.marduk.routes.netex;

import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.netex.conversion.NeTExConverter;
import no.rutebanken.marduk.netex.conversion.NeTExDowngrader;
import no.rutebanken.marduk.netex.conversion.NeTExUpgrader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StreamUtils;
import org.springframework.util.xml.StaxUtils;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.Semaphore;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static no.rutebanken.marduk.routes.file.beans.FileClassifierPredicates.NETEX_PUBLICATION_DELIVERY_QNAME;

/**
 * Convert NeTEx documents between NeTEx 1.15 and NeTEx 1.16 (DatedServiceJourney structure) with the
 * {@link NeTExDowngrader} and {@link NeTExUpgrader} from netex-java-model.
 * <p>
 * Only documents whose {@code PublicationDelivery/@version} starts with the source version of the conversion are
 * transformed; every other file is copied byte for byte. The XSLT processor loads the whole document in memory,
 * so transformations are serialized JVM-wide and refused above a configurable size.
 */
public final class NetexDsjConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetexDsjConverter.class);

    private static final XMLInputFactory XML_INPUT_FACTORY = StaxUtils.createDefensiveInputFactory();

    /**
     * At most one XSLT transformation at a time: the JDK XSLT processor holds the whole document in memory.
     */
    private static final Semaphore XSLT_PERMIT = new Semaphore(1);

    /**
     * The supported conversions.
     */
    public enum Direction {
        /**
         * NeTEx 1.16 (or later) to NeTEx 1.15.
         */
        DOWNGRADE("1.15") {
            @Override
            boolean convertsVersionKey(int declaredKey, int targetKey) {
                return declaredKey > targetKey;
            }

            @Override
            NeTExConverter converter() throws IOException, TransformerConfigurationException {
                return NeTExDowngrader.getNeTExDowngrader();
            }
        },
        /**
         * NeTEx 1.15 (or earlier) to NeTEx 1.16. DatedServiceJourney is the same element in 1.15 and in the versions
         * before it, so a dataset still produced against an older version of the profile (GOA declares 1.13)
         * is upgraded the same way.
         */
        UPGRADE("1.16") {
            @Override
            boolean convertsVersionKey(int declaredKey, int targetKey) {
                return declaredKey < targetKey;
            }

            @Override
            NeTExConverter converter() throws IOException, TransformerConfigurationException {
                return NeTExUpgrader.getNeTExUpgrader();
            }
        };

        private final String targetVersion;

        Direction(String targetVersion) {
            this.targetVersion = targetVersion;
        }

        /**
         * NeTEx version written by this conversion in the first component of {@code PublicationDelivery/@version}.
         */
        public String getTargetVersion() {
            return targetVersion;
        }

        /**
         * Whether a document declaring the given {@code PublicationDelivery/@version} is transformed by this
         * conversion, that is whether the NeTEx version it declares is on the source side of the target version.
         * A version without a recognizable version number is left as is. This mirrors the condition under which the
         * stylesheets rewrite the version attribute, so that the documents selected here are exactly the ones they
         * would change.
         */
        public boolean convertsDocumentVersion(String documentVersion) {
            OptionalInt declaredKey = netexVersionKey(documentVersion);
            OptionalInt targetKey = netexVersionKey(targetVersion);
            return declaredKey.isPresent() && targetKey.isPresent()
                    && convertsVersionKey(declaredKey.getAsInt(), targetKey.getAsInt());
        }

        abstract boolean convertsVersionKey(int declaredKey, int targetKey);

        abstract NeTExConverter converter() throws IOException, TransformerConfigurationException;
    }

    /**
     * What happened to a file.
     */
    public enum Outcome {
        /**
         * The file was not a NeTEx document on the source side of the conversion and was copied unchanged.
         */
        COPIED,
        /**
         * The file was a NeTEx document on the source side of the conversion and was converted.
         */
        CONVERTED
    }

    /**
     * Summary of the processing of a zip archive.
     *
     * @param entries   number of entries in the archive.
     * @param converted number of entries that were converted.
     */
    public record Report(int entries, int converted) {
        public int copied() {
            return entries - converted;
        }
    }

    private NetexDsjConverter() {
    }

    /**
     * Downgrade every NeTEx 1.16 (or later) document of a zip archive to NeTEx 1.15.
     *
     * @see #convertZip(Direction, InputStream, File, File, long)
     */
    public static Report downgradeZip(InputStream sourceZip, File targetZip, File tmpDir, long maxXsltInputBytes) throws IOException {
        return convertZip(Direction.DOWNGRADE, sourceZip, targetZip, tmpDir, maxXsltInputBytes);
    }

    /**
     * Upgrade every NeTEx 1.15 (or earlier) document of a zip archive to NeTEx 1.16.
     *
     * @see #convertZip(Direction, InputStream, File, File, long)
     */
    public static Report upgradeZip(InputStream sourceZip, File targetZip, File tmpDir, long maxXsltInputBytes) throws IOException {
        return convertZip(Direction.UPGRADE, sourceZip, targetZip, tmpDir, maxXsltInputBytes);
    }

    /**
     * Convert every NeTEx document on the source side of the conversion in a zip archive.
     * The archive is processed entry by entry, so the memory footprint is bounded by the largest XML file, not by
     * the size of the archive. Entry names and order are preserved.
     *
     * @param direction         the conversion to apply.
     * @param sourceZip         the source archive, consumed and closed.
     * @param targetZip         the archive to create.
     * @param tmpDir            an existing directory where each XML entry is temporarily spooled before processing.
     * @param maxXsltInputBytes largest document accepted for transformation.
     */
    public static Report convertZip(Direction direction, InputStream sourceZip, File targetZip, File tmpDir, long maxXsltInputBytes) throws IOException {
        int entries = 0;
        int converted = 0;
        try (ZipInputStream in = new ZipInputStream(sourceZip);
             ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(targetZip)))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                out.putNextEntry(new ZipEntry(entry.getName()));
                if (!entry.isDirectory()) {
                    if (isXmlFile(entry.getName())) {
                        if (convertXmlEntry(direction, in, out, tmpDir, maxXsltInputBytes) == Outcome.CONVERTED) {
                            converted++;
                        }
                    } else {
                        in.transferTo(out);
                    }
                }
                out.closeEntry();
                entries++;
            }
        }
        return new Report(entries, converted);
    }

    private static Outcome convertXmlEntry(Direction direction, ZipInputStream in, ZipOutputStream out, File tmpDir, long maxXsltInputBytes) throws IOException {
        Path tmpFile = Files.createTempFile(tmpDir.toPath(), "netex-dsj-", ".xml");
        try {
            Files.copy(in, tmpFile, StandardCopyOption.REPLACE_EXISTING);
            return convertXml(direction, tmpFile.toFile(), out, maxXsltInputBytes);
        } finally {
            Files.deleteIfExists(tmpFile);
        }
    }

    /**
     * Downgrade a single XML file if it is a NeTEx 1.16 (or later) document, otherwise copy it unchanged.
     *
     * @see #convertXml(Direction, File, OutputStream, long)
     */
    public static Outcome downgradeXml(File source, OutputStream target, long maxXsltInputBytes) throws IOException {
        return convertXml(Direction.DOWNGRADE, source, target, maxXsltInputBytes);
    }

    /**
     * Convert a single XML file if it is a NeTEx document on the source side of the conversion, otherwise copy it unchanged.
     *
     * @param direction         the conversion to apply.
     * @param source            the XML file.
     * @param target            where the result is written. The stream is not closed.
     * @param maxXsltInputBytes largest document accepted for transformation.
     */
    public static Outcome convertXml(Direction direction, File source, OutputStream target, long maxXsltInputBytes) throws IOException {
        Optional<String> version = readPublicationDeliveryVersion(source);
        if (version.isEmpty() || !direction.convertsDocumentVersion(version.get())) {
            Files.copy(source.toPath(), target);
            return Outcome.COPIED;
        }
        if (source.length() > maxXsltInputBytes) {
            throw new MardukException("Cannot convert NeTEx document of size " + source.length()
                    + " bytes: it exceeds the maximum size " + maxXsltInputBytes + " bytes supported by the in-memory XSLT transformation");
        }
        LOGGER.debug("Converting ({}) NeTEx document with version {} ({} bytes)", direction, version.get(), source.length());
        acquireXsltPermit();
        try {
            direction.converter().convert(new StreamSource(source), new StreamResult(StreamUtils.nonClosing(target)));
        } catch (TransformerException e) {
            throw new MardukException("Failed to convert (" + direction + ") NeTEx document with version " + version.get(), e);
        } finally {
            XSLT_PERMIT.release();
        }
        return Outcome.CONVERTED;
    }

    /**
     * Ordering key of the NeTEx version declared by a {@code PublicationDelivery/@version} attribute
     * ({@code "1.13:NO-NeTEx-networktimetable:1.3"}, {@code "1.16"}), so that it can be compared with the target
     * version of a conversion. The key is {@code major * 1000 + minor}; non-digit characters are ignored and anything
     * after the minor number is dropped. Empty when the attribute has no recognizable version number, in which case
     * the document is left as is. Mirrors the {@code netexVersionKey} template of the stylesheets.
     */
    static OptionalInt netexVersionKey(String documentVersion) {
        String versionNumber = documentVersion.split(":", 2)[0];
        String[] components = versionNumber.split("\\.", 3);
        OptionalInt major = digits(components[0]);
        if (major.isEmpty()) {
            return OptionalInt.empty();
        }
        int minor = components.length > 1 ? digits(components[1]).orElse(0) : 0;
        return OptionalInt.of(major.getAsInt() * 1000 + minor);
    }

    private static OptionalInt digits(String component) {
        String value = component.replaceAll("\\D", "");
        if (value.isEmpty() || value.length() > 3) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Integer.parseInt(value));
    }

    /**
     * Read the version attribute of the root {@code PublicationDelivery} element without parsing the whole document.
     *
     * @return the version attribute, empty if the root element is not a NeTEx PublicationDelivery or has no version.
     */
    static Optional<String> readPublicationDeliveryVersion(File xml) throws IOException {
        try (InputStream inputStream = Files.newInputStream(xml.toPath())) {
            XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(inputStream);
            try {
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                        if (NETEX_PUBLICATION_DELIVERY_QNAME.equals(reader.getName())) {
                            return Optional.ofNullable(reader.getAttributeValue(null, "version"));
                        }
                        return Optional.empty();
                    }
                }
                return Optional.empty();
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            throw new MardukException("Failed to read the NeTEx version of " + xml.getName(), e);
        }
    }

    private static void acquireXsltPermit() {
        try {
            XSLT_PERMIT.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MardukException("Interrupted while waiting for the XSLT transformation permit", e);
        }
    }

    private static boolean isXmlFile(String entryName) {
        return entryName.toLowerCase(Locale.ROOT).endsWith(".xml");
    }
}

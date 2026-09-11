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
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NetexDsjConverterTest {

    static final Path NETEX_1_16_FIXTURE = Path.of("src/test/resources/no/rutebanken/marduk/routes/netex/netex-1.16-dated-service-journey.xml");
    static final Path NETEX_1_15_FIXTURE = Path.of("src/test/resources/no/rutebanken/marduk/routes/netex/netex-1.15-dated-service-journey.xml");
    static final Path NETEX_ZIP_FIXTURE = Path.of("src/test/resources/no/rutebanken/marduk/routes/file/beans/netex.zip");
    static final String NETEX_1_16_ENTRY = "VYG_dated-service-journeys.xml";
    static final String OLD_NETEX_ENTRY = "WF739.xml";
    static final String README_ENTRY = "README.txt";

    private static final long NO_SIZE_LIMIT = Long.MAX_VALUE;

    @TempDir
    File tmpDir;

    /**
     * A zip archive with a NeTEx 1.16 file, a file in an older NeTEx version and a non-XML file.
     */
    static Map<String, byte[]> mixedArchiveEntries() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(NETEX_1_16_ENTRY, Files.readAllBytes(NETEX_1_16_FIXTURE));
        entries.put(OLD_NETEX_ENTRY, ZipFileUtils.extractFileFromZipFile(NETEX_ZIP_FIXTURE.toFile(), OLD_NETEX_ENTRY));
        entries.put(README_ENTRY, "not xml".getBytes(StandardCharsets.UTF_8));
        return entries;
    }

    static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    static Map<String, byte[]> unzip(byte[] archive) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }

    @Test
    void downgradeZipTransformsOnlyNetex116Documents() throws IOException {
        Map<String, byte[]> source = mixedArchiveEntries();
        File target = new File(tmpDir, "legacy.zip");

        NetexDsjConverter.Report report = NetexDsjConverter.downgradeZip(new ByteArrayInputStream(zip(source)), target, tmpDir, NO_SIZE_LIMIT);

        assertThat(report.entries()).isEqualTo(3);
        assertThat(report.converted()).isEqualTo(1);
        assertThat(report.copied()).isEqualTo(2);

        Map<String, byte[]> result = unzip(Files.readAllBytes(target.toPath()));
        assertThat(result.keySet()).containsExactly(NETEX_1_16_ENTRY, OLD_NETEX_ENTRY, README_ENTRY);
        assertThat(result.get(OLD_NETEX_ENTRY)).isEqualTo(source.get(OLD_NETEX_ENTRY));
        assertThat(result.get(README_ENTRY)).isEqualTo(source.get(README_ENTRY));
        assertDowngraded(new String(result.get(NETEX_1_16_ENTRY), StandardCharsets.UTF_8));
    }

    @Test
    void downgradeXmlRewritesDatedServiceJourneys() throws IOException {
        ByteArrayOutputStream target = new ByteArrayOutputStream();

        NetexDsjConverter.Outcome outcome = NetexDsjConverter.downgradeXml(NETEX_1_16_FIXTURE.toFile(), target, NO_SIZE_LIMIT);

        assertThat(outcome).isEqualTo(NetexDsjConverter.Outcome.CONVERTED);
        assertDowngraded(target.toString(StandardCharsets.UTF_8));
    }

    @Test
    void downgradeXmlCopiesOtherVersionsUnchanged() throws IOException {
        byte[] source = Files.readAllBytes(NETEX_1_16_FIXTURE);
        byte[] netex115 = new String(source, StandardCharsets.UTF_8).replace("version=\"1.16:", "version=\"1.15:").getBytes(StandardCharsets.UTF_8);
        File netex115File = new File(tmpDir, "netex-1.15.xml");
        Files.write(netex115File.toPath(), netex115);
        ByteArrayOutputStream target = new ByteArrayOutputStream();

        NetexDsjConverter.Outcome outcome = NetexDsjConverter.downgradeXml(netex115File, target, NO_SIZE_LIMIT);

        assertThat(outcome).isEqualTo(NetexDsjConverter.Outcome.COPIED);
        assertThat(target.toByteArray()).isEqualTo(netex115);
    }

    @Test
    void downgradeXmlRejectsDocumentsLargerThanTheLimit() {
        ByteArrayOutputStream target = new ByteArrayOutputStream();

        assertThatThrownBy(() -> NetexDsjConverter.downgradeXml(NETEX_1_16_FIXTURE.toFile(), target, 10))
                .isInstanceOf(MardukException.class)
                .hasMessageContaining("exceeds the maximum size");
        assertThat(target.size()).isZero();
    }

    @Test
    void readPublicationDeliveryVersion() throws IOException {
        assertThat(NetexDsjConverter.readPublicationDeliveryVersion(NETEX_1_16_FIXTURE.toFile()))
                .contains("1.16:NO-NeTEx-networktimetable:1.3");

        File notNetex = new File(tmpDir, "other.xml");
        Files.writeString(notNetex.toPath(), "<?xml version=\"1.0\"?><other version=\"1.16\"/>");
        assertThat(NetexDsjConverter.readPublicationDeliveryVersion(notNetex)).isEmpty();
    }

    @Test
    void upgradeXmlRewritesDatedServiceJourneys() throws IOException {
        ByteArrayOutputStream target = new ByteArrayOutputStream();

        NetexDsjConverter.Outcome outcome = NetexDsjConverter.convertXml(NetexDsjConverter.Direction.UPGRADE, NETEX_1_15_FIXTURE.toFile(), target, NO_SIZE_LIMIT);

        assertThat(outcome).isEqualTo(NetexDsjConverter.Outcome.CONVERTED);
        assertUpgraded(target.toString(StandardCharsets.UTF_8));
    }

    @Test
    void upgradeXmlCopiesNetex116Unchanged() throws IOException {
        ByteArrayOutputStream target = new ByteArrayOutputStream();

        NetexDsjConverter.Outcome outcome = NetexDsjConverter.convertXml(NetexDsjConverter.Direction.UPGRADE, NETEX_1_16_FIXTURE.toFile(), target, NO_SIZE_LIMIT);

        assertThat(outcome).isEqualTo(NetexDsjConverter.Outcome.COPIED);
        assertThat(target.toByteArray()).isEqualTo(Files.readAllBytes(NETEX_1_16_FIXTURE));
    }

    @Test
    void upgradeZipConvertsOnlyNetex115Documents() throws IOException {
        Map<String, byte[]> source = new LinkedHashMap<>();
        source.put("VYG_netex-1.15.xml", Files.readAllBytes(NETEX_1_15_FIXTURE));
        source.put(NETEX_1_16_ENTRY, Files.readAllBytes(NETEX_1_16_FIXTURE));
        source.put(README_ENTRY, "not xml".getBytes(StandardCharsets.UTF_8));
        File target = new File(tmpDir, "upgraded.zip");

        NetexDsjConverter.Report report = NetexDsjConverter.upgradeZip(new ByteArrayInputStream(zip(source)), target, tmpDir, NO_SIZE_LIMIT);

        assertThat(report.entries()).isEqualTo(3);
        assertThat(report.converted()).isEqualTo(1);
        Map<String, byte[]> result = unzip(Files.readAllBytes(target.toPath()));
        assertThat(result.keySet()).containsExactly("VYG_netex-1.15.xml", NETEX_1_16_ENTRY, README_ENTRY);
        assertUpgraded(new String(result.get("VYG_netex-1.15.xml"), StandardCharsets.UTF_8));
        assertThat(result.get(NETEX_1_16_ENTRY)).isEqualTo(source.get(NETEX_1_16_ENTRY));
        assertThat(result.get(README_ENTRY)).isEqualTo(source.get(README_ENTRY));
    }

    /**
     * DatedServiceJourney is the same element in 1.15 and in the versions before it, so a dataset produced against an
     * older version of the profile (GOA declares 1.13) is upgraded the same way.
     */
    @Test
    void upgradeXmlConvertsDocumentsDeclaringAVersionOlderThan115() throws IOException {
        File netex113File = fixtureDeclaring(NETEX_1_15_FIXTURE, "1.15:", "1.13:");
        ByteArrayOutputStream target = new ByteArrayOutputStream();

        NetexDsjConverter.Outcome outcome = NetexDsjConverter.convertXml(NetexDsjConverter.Direction.UPGRADE, netex113File, target, NO_SIZE_LIMIT);

        assertThat(outcome).isEqualTo(NetexDsjConverter.Outcome.CONVERTED);
        assertUpgraded(target.toString(StandardCharsets.UTF_8));
    }

    @Test
    void downgradeXmlCopiesDocumentsDeclaringAVersionOlderThan116() throws IOException {
        File netex113File = fixtureDeclaring(NETEX_1_15_FIXTURE, "1.15:", "1.13:");
        ByteArrayOutputStream target = new ByteArrayOutputStream();

        NetexDsjConverter.Outcome outcome = NetexDsjConverter.downgradeXml(netex113File, target, NO_SIZE_LIMIT);

        assertThat(outcome).isEqualTo(NetexDsjConverter.Outcome.COPIED);
        assertThat(target.toByteArray()).isEqualTo(Files.readAllBytes(netex113File.toPath()));
    }

    @Test
    void theVersionsSelectedByEachConversion() {
        assertThat(NetexDsjConverter.Direction.UPGRADE.convertsDocumentVersion("1.13:NO-NeTEx-networktimetable:1.3")).isTrue();
        assertThat(NetexDsjConverter.Direction.UPGRADE.convertsDocumentVersion("1.07:NO-NeTEx-networktimetable:1.1")).isTrue();
        assertThat(NetexDsjConverter.Direction.UPGRADE.convertsDocumentVersion("1.15:NO-NeTEx-networktimetable:1.5")).isTrue();
        assertThat(NetexDsjConverter.Direction.UPGRADE.convertsDocumentVersion("1.16:NO-NeTEx-networktimetable:1.6")).isFalse();
        assertThat(NetexDsjConverter.Direction.UPGRADE.convertsDocumentVersion("1.17")).isFalse();

        assertThat(NetexDsjConverter.Direction.DOWNGRADE.convertsDocumentVersion("1.16:NO-NeTEx-networktimetable:1.6")).isTrue();
        assertThat(NetexDsjConverter.Direction.DOWNGRADE.convertsDocumentVersion("1.17")).isTrue();
        assertThat(NetexDsjConverter.Direction.DOWNGRADE.convertsDocumentVersion("1.15:NO-NeTEx-networktimetable:1.5")).isFalse();
        assertThat(NetexDsjConverter.Direction.DOWNGRADE.convertsDocumentVersion("1.13:NO-NeTEx-networktimetable:1.3")).isFalse();

        // a version without a recognizable version number leaves the document untouched
        assertThat(NetexDsjConverter.Direction.UPGRADE.convertsDocumentVersion("")).isFalse();
        assertThat(NetexDsjConverter.Direction.UPGRADE.convertsDocumentVersion("any")).isFalse();
        assertThat(NetexDsjConverter.Direction.DOWNGRADE.convertsDocumentVersion("any")).isFalse();
    }

    private File fixtureDeclaring(Path fixture, String version, String otherVersion) throws IOException {
        String xml = Files.readString(fixture).replace("version=\"" + version, "version=\"" + otherVersion);
        File file = new File(tmpDir, "netex-" + otherVersion.replace(":", "") + ".xml");
        Files.writeString(file.toPath(), xml);
        return file;
    }

    static void assertUpgraded(String xml) {
        // the conversion also rewrites the Nordic profile version in the last segment
        assertThat(xml).contains("version=\"1.16:NO-NeTEx-networktimetable:1.6\"");
        assertThat(xml).contains("<replacedJourneys>");
        assertThat(xml).doesNotContain("<DatedServiceJourneyRef");
    }

    static void assertDowngraded(String xml) {
        // the conversion also rewrites the Nordic profile version in the last segment
        assertThat(xml).contains("version=\"1.15:NO-NeTEx-networktimetable:1.5\"");
        assertThat(xml).doesNotContain("<replacedJourneys");
        assertThat(xml).doesNotContain("<DatedVehicleJourneyRef");
        assertThat(xml).contains("<DatedServiceJourneyRef ref=\"VYG:DatedServiceJourney:8916_KVG-DEG_23-10-19\" version=\"1\"/>");
    }
}

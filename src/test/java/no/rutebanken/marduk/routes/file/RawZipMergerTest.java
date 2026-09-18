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

package no.rutebanken.marduk.routes.file;

import no.rutebanken.marduk.exceptions.MardukException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RawZipMergerTest {

    @TempDir
    Path tempDir;

    /**
     * The exports produced by Chouette set the data descriptor bit (general purpose bit 3) on every entry, because
     * they are written to a stream whose sizes are not known up front. The raw copy must read the sizes from the
     * central directory and write them inline, clearing that bit.
     */
    @Test
    void copiesEntriesWrittenWithADataDescriptor() throws IOException {
        byte[] archive = deflatedArchive(Map.of("ATB_ATB-Line-1.xml", "<PublicationDelivery/>".repeat(50)));
        assertThat(generalPurposeBits(archive)).allMatch(flags -> (flags & 0x8) != 0, "source uses data descriptors");

        Path merged = merge(List.of(source("atb", archive)));

        assertThat(generalPurposeBits(Files.readAllBytes(merged)))
                .allMatch(flags -> (flags & 0x8) == 0, "merged archive has no data descriptor");
        assertThat(entries(merged)).containsEntry("ATB_ATB-Line-1.xml", "<PublicationDelivery/>".repeat(50));
    }

    @Test
    void copiesStoredAndDeflatedEntries() throws IOException {
        byte[] archive = mixedArchive();

        Path merged = merge(List.of(source("mixed", archive)));

        assertThat(entries(merged)).containsExactlyInAnyOrderEntriesOf(
                Map.of("stored.xml", "stored content", "deflated.xml", "deflated content"));
        try (ZipFile zip = new ZipFile(merged.toFile())) {
            assertThat(zip.getEntry("stored.xml").getMethod()).isEqualTo(ZipEntry.STORED);
            assertThat(zip.getEntry("deflated.xml").getMethod()).isEqualTo(ZipEntry.DEFLATED);
        }
    }

    @Test
    void mergesSeveralArchivesInPlanOrder() throws IOException {
        byte[] first = deflatedArchive(ordered("ATB_a.xml", "a", "ATB_b.xml", "b"));
        byte[] second = deflatedArchive(ordered("RUT_c.xml", "c"));

        Path merged = merge(List.of(source("atb", first), source("rut", second)));

        assertThat(entries(merged).keySet()).containsExactly("ATB_a.xml", "ATB_b.xml", "RUT_c.xml");
    }

    @Test
    void renamesTheStopPlaceEntries() throws IOException {
        byte[] stops = deflatedArchive(ordered("tiamat-export-1.xml", "one", "tiamat-export-2.xml", "two"));

        Path merged = tempDir.resolve("merged.zip");
        try (RawZipMerger.Plan plan = RawZipMerger.plan()) {
            plan.addRenamed(source("stops", stops), "_stops", ".xml").writeTo(merged);
        }

        assertThat(entries(merged)).containsExactlyInAnyOrderEntriesOf(
                Map.of("_stops.xml", "one", "_stops1.xml", "two"));
    }

    /**
     * The previous implementation unpacked every archive into one directory, so an entry contributed twice was
     * silently replaced by the last one. That behaviour is reproduced, and reported.
     */
    @Test
    void keepsTheLastOfTwoEntriesWithTheSameName() throws IOException {
        byte[] first = deflatedArchive(ordered("shared.xml", "from the first archive"));
        byte[] second = deflatedArchive(ordered("shared.xml", "from the second archive"));

        Path merged = tempDir.resolve("merged.zip");
        RawZipMerger.Result result;
        try (RawZipMerger.Plan plan = RawZipMerger.plan()) {
            result = plan.add(source("first", first)).add(source("second", second)).writeTo(merged);
        }

        assertThat(result.duplicateNames()).containsExactly("shared.xml");
        assertThat(entries(merged)).containsExactly(Map.entry("shared.xml", "from the second archive"));
    }

    @Test
    void failsOnDuplicateEntryNamesWhenConfiguredTo() throws IOException {
        try (RawZipMerger.Plan plan = RawZipMerger.plan().failOnDuplicateEntry(true)) {
            plan.add(source("first", deflatedArchive(ordered("shared.xml", "one"))));
            plan.add(source("second", deflatedArchive(ordered("shared.xml", "two"))));
            Path target = tempDir.resolve("merged.zip");
            assertThatThrownBy(() -> plan.writeTo(target))
                    .isInstanceOf(MardukException.class)
                    .hasMessageContaining("shared.xml");
        }
    }

    @Test
    void failsWithTheLabelOfAnArchiveThatIsNotAZip() {
        assertThatThrownBy(() -> RawZipMerger.Source.of("rb_rut-aggregated-netex.zip", "not a zip archive".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(MardukException.class)
                .hasMessageContaining("rb_rut-aggregated-netex.zip");
    }

    @Test
    void rejectsAnEncryptedEntry() throws IOException {
        byte[] archive = encryptedArchive();
        try (RawZipMerger.Plan plan = RawZipMerger.plan()) {
            RawZipMerger.Source encrypted = source("encrypted", archive);
            assertThatThrownBy(() -> plan.add(encrypted))
                    .isInstanceOf(MardukException.class)
                    .hasMessageContaining("encrypted");
        }
    }

    /**
     * The plan owns the sources added to it, including one whose entries it rejected: closing the plan closes them,
     * so a merge needs no bookkeeping of its own to avoid leaking an open archive.
     */
    @Test
    void closesItsSourcesWhenClosed() throws IOException {
        RawZipMerger.Source accepted = source("accepted", deflatedArchive(ordered("a.xml", "a")));
        RawZipMerger.Source rejected = source("rejected", encryptedArchive());
        RawZipMerger.Plan plan = RawZipMerger.plan().add(accepted);
        assertThatThrownBy(() -> plan.add(rejected)).isInstanceOf(MardukException.class);

        plan.close();

        assertThat(isClosed(accepted)).as("an accepted source is closed with the plan").isTrue();
        assertThat(isClosed(rejected)).as("a rejected source is closed with the plan").isTrue();
    }

    /**
     * The stop place names the profile mandates are positional, so a nested entry must not shift them. The replaced
     * implementation listed the unpacked folder without recursing and never saw one either.
     */
    @Test
    void ignoresNestedEntriesWhenRenaming() throws IOException {
        byte[] archive = deflatedArchive(ordered("a.xml", "first", "nested/b.xml", "nested", "c.xml", "second"));
        Path merged = tempDir.resolve("merged.zip");
        try (RawZipMerger.Plan plan = RawZipMerger.plan()) {
            plan.addRenamed(source("stops", archive), "_stops", ".xml").writeTo(merged);
        }

        assertThat(entries(merged)).containsExactlyInAnyOrderEntriesOf(
                Map.of("_stops.xml", "first", "_stops1.xml", "second"));
    }

    // --- fixtures ---

    private RawZipMerger.Source source(String label, byte[] content) {
        return RawZipMerger.Source.of(label, content);
    }

    /**
     * Whether the archive of the source has been closed: reading an entry from a closed archive fails.
     */
    private static boolean isClosed(RawZipMerger.Source source) {
        try (var in = source.archive().getRawInputStream(source.archive().getEntriesInPhysicalOrder().nextElement())) {
            in.read();
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    private Path merge(List<RawZipMerger.Source> sources) throws IOException {
        Path merged = tempDir.resolve("merged-" + sources.hashCode() + ".zip");
        try (RawZipMerger.Plan plan = RawZipMerger.plan()) {
            sources.forEach(plan::add);
            plan.writeTo(merged);
        }
        return merged;
    }

    private static Map<String, String> ordered(String... namesAndContents) {
        Map<String, String> entries = new LinkedHashMap<>();
        for (int i = 0; i < namesAndContents.length; i += 2) {
            entries.put(namesAndContents[i], namesAndContents[i + 1]);
        }
        return entries;
    }

    /**
     * An archive written to a stream, which is what Chouette does: every entry gets a data descriptor.
     */
    private static byte[] deflatedArchive(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                out.putArchiveEntry(new ZipArchiveEntry(entry.getKey()));
                out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeArchiveEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] mixedArchive() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(bytes)) {
            byte[] stored = "stored content".getBytes(StandardCharsets.UTF_8);
            ZipArchiveEntry storedEntry = new ZipArchiveEntry("stored.xml");
            storedEntry.setMethod(ZipEntry.STORED);
            storedEntry.setSize(stored.length);
            storedEntry.setCompressedSize(stored.length);
            CRC32 crc = new CRC32();
            crc.update(stored);
            storedEntry.setCrc(crc.getValue());
            out.putArchiveEntry(storedEntry);
            out.write(stored);
            out.closeArchiveEntry();

            out.putArchiveEntry(new ZipArchiveEntry("deflated.xml"));
            out.write("deflated content".getBytes(StandardCharsets.UTF_8));
            out.closeArchiveEntry();
        }
        return bytes.toByteArray();
    }

    /**
     * An archive whose entry claims to be encrypted, by setting general purpose bit 0 in both headers.
     */
    private static byte[] encryptedArchive() throws IOException {
        byte[] archive = deflatedArchive(ordered("encrypted.xml", "content"));
        for (int i = 0; i + 3 < archive.length; i++) {
            int signature = (archive[i] & 0xFF) | (archive[i + 1] & 0xFF) << 8
                    | (archive[i + 2] & 0xFF) << 16 | (archive[i + 3] & 0xFF) << 24;
            if (signature == 0x04034b50) {
                archive[i + 6] |= 0x01;
            } else if (signature == 0x02014b50) {
                archive[i + 8] |= 0x01;
            }
        }
        return archive;
    }

    private static List<Integer> generalPurposeBits(byte[] archive) {
        List<Integer> flags = new ArrayList<>();
        for (int i = 0; i + 7 < archive.length; i++) {
            int signature = (archive[i] & 0xFF) | (archive[i + 1] & 0xFF) << 8
                    | (archive[i + 2] & 0xFF) << 16 | (archive[i + 3] & 0xFF) << 24;
            if (signature == 0x04034b50) {
                flags.add((archive[i + 6] & 0xFF) | (archive[i + 7] & 0xFF) << 8);
            }
        }
        return flags;
    }

    private static Map<String, String> entries(Path archive) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            for (ZipEntry entry : zip.stream().toList()) {
                try (var in = zip.getInputStream(entry)) {
                    entries.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        return entries;
    }
}

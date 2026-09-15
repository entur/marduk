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
        assertThat(entries(merged)).containsOnlyKeys("ATB_ATB-Line-1.xml");
        assertThat(entries(merged).get("ATB_ATB-Line-1.xml")).isEqualTo("<PublicationDelivery/>".repeat(50));
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

        List<RawZipMerger.Source> sources = List.of(source("stops", stops));
        Path merged = tempDir.resolve("merged.zip");
        try {
            RawZipMerger.plan().addRenamed(sources.getFirst(), "_stops", ".xml").writeTo(List.of(merged));
        } finally {
            RawZipMerger.closeAll(sources);
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

        List<RawZipMerger.Source> sources = List.of(source("first", first), source("second", second));
        Path merged = tempDir.resolve("merged.zip");
        RawZipMerger.Result result;
        try {
            RawZipMerger.Plan plan = RawZipMerger.plan();
            sources.forEach(plan::add);
            result = plan.writeTo(List.of(merged));
        } finally {
            RawZipMerger.closeAll(sources);
        }

        assertThat(result.duplicateNames()).containsExactly("shared.xml");
        assertThat(entries(merged)).containsExactly(Map.entry("shared.xml", "from the second archive"));
    }

    @Test
    void failsOnDuplicateEntryNamesWhenConfiguredTo() throws IOException {
        List<RawZipMerger.Source> sources = List.of(
                source("first", deflatedArchive(ordered("shared.xml", "one"))),
                source("second", deflatedArchive(ordered("shared.xml", "two"))));
        try {
            RawZipMerger.Plan plan = RawZipMerger.plan().failOnDuplicateEntry(true);
            sources.forEach(plan::add);
            assertThatThrownBy(() -> plan.writeTo(List.of(tempDir.resolve("merged.zip"))))
                    .isInstanceOf(MardukException.class)
                    .hasMessageContaining("shared.xml");
        } finally {
            RawZipMerger.closeAll(sources);
        }
    }

    /**
     * The dual DatedServiceJourney export builds both variants in one pass. A provider whose two variants are the
     * same object contributes the same entries to both aggregated exports.
     */
    @Test
    void writesSeveralOutputsInOnePass() throws IOException {
        byte[] shared = deflatedArchive(ordered("ATB_a.xml", "same in both variants"));
        byte[] legacy = deflatedArchive(ordered("VYG_a.xml", "downgraded"));
        byte[] current = deflatedArchive(ordered("VYG_a.xml", "not downgraded"));

        List<RawZipMerger.Source> sources = List.of(
                source("atb", shared), source("vyg-legacy", legacy), source("vyg-new", current));
        Path legacyMerged = tempDir.resolve("legacy.zip");
        Path newMerged = tempDir.resolve("new.zip");
        try {
            RawZipMerger.plan()
                    .add(sources.getFirst())
                    .add(List.of(sources.get(1), sources.get(2)))
                    .writeTo(List.of(legacyMerged, newMerged));
        } finally {
            RawZipMerger.closeAll(sources);
        }

        assertThat(entries(legacyMerged)).containsExactlyInAnyOrderEntriesOf(
                Map.of("ATB_a.xml", "same in both variants", "VYG_a.xml", "downgraded"));
        assertThat(entries(newMerged)).containsExactlyInAnyOrderEntriesOf(
                Map.of("ATB_a.xml", "same in both variants", "VYG_a.xml", "not downgraded"));
    }

    @Test
    void rejectsAnEncryptedEntry() throws IOException {
        byte[] archive = encryptedArchive();
        List<RawZipMerger.Source> sources = List.of(source("encrypted", archive));
        try {
            RawZipMerger.Plan plan = RawZipMerger.plan();
            assertThatThrownBy(() -> plan.add(sources.getFirst()))
                    .isInstanceOf(MardukException.class)
                    .hasMessageContaining("encrypted");
        } finally {
            RawZipMerger.closeAll(sources);
        }
    }

    @Test
    void failsWhenAVariantDoesNotContainTheSameEntries() throws IOException {
        List<RawZipMerger.Source> sources = List.of(
                source("legacy", deflatedArchive(ordered("VYG_a.xml", "one"))),
                source("new", deflatedArchive(ordered("VYG_b.xml", "two"))));
        try {
            RawZipMerger.Plan plan = RawZipMerger.plan();
            assertThatThrownBy(() -> plan.add(List.of(sources.getFirst(), sources.get(1))))
                    .isInstanceOf(MardukException.class)
                    .hasMessageContaining("VYG_a.xml");
        } finally {
            RawZipMerger.closeAll(sources);
        }
    }

    @Test
    void spillsAnArchiveLargerThanTheThresholdToDisk() throws IOException {
        byte[] archive = deflatedArchive(ordered("ATB_a.xml", "content"));
        Path spillDirectory = tempDir.resolve("spill");

        Path merged = tempDir.resolve("merged.zip");
        RawZipMerger.Source source = RawZipMerger.Source.of("atb", archive, 0, spillDirectory);
        try {
            assertThat(Files.list(spillDirectory)).hasSize(1);
            RawZipMerger.plan().add(source).writeTo(List.of(merged));
        } finally {
            source.close();
        }

        assertThat(entries(merged)).containsExactly(Map.entry("ATB_a.xml", "content"));
        assertThat(Files.list(spillDirectory)).isEmpty();
    }

    // --- fixtures ---

    private RawZipMerger.Source source(String label, byte[] content) throws IOException {
        return RawZipMerger.Source.of(label, content, Long.MAX_VALUE, tempDir.resolve("spill"));
    }

    private Path merge(List<RawZipMerger.Source> sources) throws IOException {
        Path merged = tempDir.resolve("merged-" + sources.hashCode() + ".zip");
        try {
            RawZipMerger.Plan plan = RawZipMerger.plan();
            sources.forEach(plan::add);
            plan.writeTo(List.of(merged));
        } finally {
            RawZipMerger.closeAll(sources);
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

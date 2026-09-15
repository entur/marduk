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
import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges several zip archives into one or more aggregated archives by copying the entries
 * <em>without decompressing them</em>.
 * <p>
 * The per-provider NeTEx exports are already deflated and the aggregated Norway export does not touch their
 * contents, so inflating every entry only to deflate it again is pure waste: it is by far the dominant cost of the
 * aggregated export. {@link ZipFile#getRawInputStream(ZipArchiveEntry)} hands out the compressed bytes of an entry
 * and {@link ZipArchiveOutputStream#addRawArchiveEntry(ZipArchiveEntry, InputStream)} writes them back verbatim,
 * carrying over the method, the sizes and the CRC. {@code java.util.zip} offers no equivalent: its
 * {@code ZipOutputStream} always feeds what is written to it through a {@code Deflater}.
 * <p>
 * Entries read through {@link ZipFile} come from the central directory, where the CRC and both sizes are always
 * present, so {@code addRawArchiveEntry} always takes its two-phase path. That also normalises away the data
 * descriptors that the Chouette exports carry (general purpose bit 3): the copy gets the real sizes inline in its
 * local file header and no trailing descriptor.
 * <p>
 * The merge is resolved into a {@link Plan} before anything is written, so that entries contributed under the same
 * name by several archives can be resolved the way the previous implementation did: it unpacked every archive into
 * one flat directory, where the last writer silently won.
 */
public final class RawZipMerger {

    private static final Logger LOGGER = LoggerFactory.getLogger(RawZipMerger.class);

    private RawZipMerger() {
    }

    public static Plan plan() {
        return new Plan();
    }

    /**
     * A source archive, open for the duration of the merge: every output reads its entries from it.
     * <p>
     * The archive is held in memory rather than spooled to disk, since the blob store hands out the whole blob as a
     * byte array anyway; writing it out would only add a disk round trip. Archives above
     * {@code spillThresholdBytes} are spilled to disk so that an unusually large export cannot blow up the heap.
     */
    public static final class Source implements Closeable {

        private final String label;
        private final ZipFile archive;
        private final Path spillFile;

        private Source(String label, ZipFile archive, Path spillFile) {
            this.label = label;
            this.archive = archive;
            this.spillFile = spillFile;
        }

        public static Source of(String label, byte[] content, long spillThresholdBytes, Path spillDirectory)
                throws IOException {
            if (content.length > spillThresholdBytes) {
                Files.createDirectories(spillDirectory);
                Path file = spillDirectory.resolve(label.replace('/', '_'));
                Files.write(file, content);
                return new Source(label, ZipFile.builder().setPath(file).setCharset(StandardCharsets.UTF_8).get(), file);
            }
            return new Source(label, ZipFile.builder()
                    .setSeekableByteChannel(new SeekableInMemoryByteChannel(content))
                    .setCharset(StandardCharsets.UTF_8)
                    .get(), null);
        }

        public String label() {
            return label;
        }

        @Override
        public void close() throws IOException {
            try {
                archive.close();
            } finally {
                if (spillFile != null) {
                    Files.deleteIfExists(spillFile);
                }
            }
        }
    }

    /**
     * One entry of the aggregated archive: the entry to read, per output.
     */
    private record Resolved(List<Source> sources, List<ZipArchiveEntry> entries, ZipArchiveEntry target) {
        Resolved {
            if (sources.size() != 1 && sources.size() != entries.size()) {
                throw new IllegalArgumentException("one entry per source is required");
            }
        }
    }

    public record Result(int entriesWritten, List<String> duplicateNames, long[] bytesWritten) {
    }

    public static final class Plan {

        private final Map<String, Resolved> entries = new LinkedHashMap<>();
        private final List<String> duplicateNames = new ArrayList<>();
        private boolean failOnDuplicateEntry;
        private int targets = -1;

        private Plan() {
        }

        public Plan failOnDuplicateEntry(boolean fail) {
            this.failOnDuplicateEntry = fail;
            return this;
        }

        /**
         * Contribute every entry of one archive, under its own name, to every output.
         */
        public Plan add(Source source) {
            return add(List.of(source));
        }

        /**
         * Contribute every entry of one archive per output, under its own name. The archives must hold the same
         * entry names: they are the variants of the same provider export.
         */
        public Plan add(List<Source> perTarget) {
            checkTargetCount(perTarget.size());
            Enumeration<ZipArchiveEntry> sourceEntries = perTarget.getFirst().archive.getEntriesInPhysicalOrder();
            while (sourceEntries.hasMoreElements()) {
                ZipArchiveEntry entry = sourceEntries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                put(entry.getName(), perTarget, entryPerTarget(perTarget, entry.getName()), null);
            }
            return this;
        }

        /**
         * Contribute every entry of one archive, renamed {@code prefix + suffix}, {@code prefix + 1 + suffix}, ... in
         * central directory order. The stop place export is published under names the NeTEx profile mandates.
         */
        public Plan addRenamed(Source source, String prefix, String suffix) {
            checkTargetCount(1);
            int i = 0;
            Enumeration<ZipArchiveEntry> sourceEntries = source.archive.getEntriesInPhysicalOrder();
            while (sourceEntries.hasMoreElements()) {
                ZipArchiveEntry entry = sourceEntries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = prefix + (i > 0 ? i : "") + suffix;
                put(name, List.of(source), List.of(entry), renamed(entry, name));
                i++;
            }
            return this;
        }

        private void checkTargetCount(int count) {
            if (count == 1) {
                if (targets < 0) {
                    targets = 1;
                }
                return;
            }
            if (targets > 1 && targets != count) {
                throw new MardukException("Cannot merge " + count + " archive variants into " + targets + " outputs");
            }
            targets = count;
        }

        private List<ZipArchiveEntry> entryPerTarget(List<Source> perTarget, String name) {
            List<ZipArchiveEntry> perTargetEntry = new ArrayList<>(perTarget.size());
            for (Source source : perTarget) {
                ZipArchiveEntry entry = source.archive.getEntry(name);
                if (entry == null) {
                    throw new MardukException("Entry " + name + " is missing from the variant " + source.label()
                            + " of a provider export: the aggregated exports would not have the same content");
                }
                perTargetEntry.add(entry);
            }
            return perTargetEntry;
        }

        private void put(String name, List<Source> sources, List<ZipArchiveEntry> entries, ZipArchiveEntry target) {
            for (ZipArchiveEntry entry : entries) {
                if (entry.getGeneralPurposeBit().usesEncryption()) {
                    // addRawArchiveEntry rebuilds the general purpose bit flag instead of copying it, so an
                    // encrypted entry would silently lose its flag and become unreadable.
                    throw new MardukException("Entry " + entry.getName() + " of " + name + " is encrypted "
                            + "and cannot be copied into the aggregated export");
                }
            }
            Resolved previous = this.entries.put(name, new Resolved(sources, entries, target));
            if (previous != null) {
                duplicateNames.add(name);
                LOGGER.warn("Entry {} is contributed by both {} and {}: keeping the latter, as the previous "
                                + "implementation did by unpacking every archive into one directory",
                        name, previous.sources().getFirst().label(), sources.getFirst().label());
            }
        }

        /**
         * Write the aggregated archives. All outputs are written in one pass over the sources, so that a variant that
         * shares a source with another variant is read only once.
         */
        public Result writeTo(List<Path> targetPaths) throws IOException {
            if (targets > 1 && targetPaths.size() != targets) {
                throw new MardukException("The merge was planned for " + targets + " outputs but "
                        + targetPaths.size() + " were given");
            }
            if (failOnDuplicateEntry && !duplicateNames.isEmpty()) {
                throw new MardukException("The aggregated export would contain duplicate entries: " + duplicateNames);
            }
            List<ZipArchiveOutputStream> outputs = new ArrayList<>(targetPaths.size());
            try {
                for (Path targetPath : targetPaths) {
                    ZipArchiveOutputStream out = new ZipArchiveOutputStream(targetPath.toFile());
                    out.setEncoding(StandardCharsets.UTF_8.name());
                    out.setUseZip64(Zip64Mode.AsNeeded);
                    outputs.add(out);
                }
                for (Resolved resolved : entries.values()) {
                    for (int target = 0; target < outputs.size(); target++) {
                        // a source shared by every output contributes the same entry to each of them
                        int source = resolved.sources().size() == 1 ? 0 : target;
                        ZipArchiveEntry entry = resolved.entries().get(source);
                        // a renamed entry (the stop place export) is written under the planned name
                        ZipArchiveEntry written = resolved.target() != null ? resolved.target() : entry;
                        try (InputStream raw = resolved.sources().get(source).archive.getRawInputStream(entry)) {
                            outputs.get(target).addRawArchiveEntry(written, raw);
                        }
                    }
                }
            } finally {
                for (ZipArchiveOutputStream out : outputs) {
                    out.close();
                }
            }
            long[] bytesWritten = new long[targetPaths.size()];
            for (int i = 0; i < targetPaths.size(); i++) {
                bytesWritten[i] = Files.size(targetPaths.get(i));
            }
            return new Result(entries.size(), List.copyOf(duplicateNames), bytesWritten);
        }
    }

    /**
     * A copy of the entry under a new name, carrying everything the raw copy needs.
     * <p>
     * {@code ZipArchiveEntry#setName} is not visible outside its package, so the entry has to be rebuilt. The
     * method matters most: a fresh entry has none, and the output stream would then silently deflate its own
     * default, contradicting the already-compressed bytes being copied.
     */
    static ZipArchiveEntry renamed(ZipArchiveEntry source, String name) {
        ZipArchiveEntry target = new ZipArchiveEntry(name);
        target.setMethod(source.getMethod());
        target.setSize(source.getSize());
        target.setCompressedSize(source.getCompressedSize());
        target.setCrc(source.getCrc());
        target.setTime(source.getTime());
        return target;
    }

    /**
     * Close every source, reporting the first failure but always closing the rest.
     */
    public static void closeAll(List<Source> sources) {
        IOException failure = null;
        for (Source source : sources) {
            try {
                source.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw new UncheckedIOException(failure);
        }
    }
}

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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges several zip archives into one aggregated archive by copying the entries
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
 * one flat directory, where the last writer silently won. The plan owns the sources added to it and closes them
 * when it is closed, so a merge is one try-with-resources block.
 * <p>
 * Archives are merged in the order they are added and, within each, in physical (local header offset) order, so the
 * aggregated export does not depend on the order in which the downloads happened to complete.
 */
public final class RawZipMerger {

    private static final Logger LOGGER = LoggerFactory.getLogger(RawZipMerger.class);

    private RawZipMerger() {
    }

    public static Plan plan() {
        return new Plan();
    }

    /**
     * A source archive, open for the duration of the merge: the output reads its entries from it.
     * <p>
     * The archive is held in memory rather than spooled to disk, since the blob store hands out the whole blob as a
     * byte array anyway; writing it out would only add a disk round trip.
     */
    public static final class Source implements Closeable {

        private final String label;
        private final ZipFile archive;

        private Source(String label, ZipFile archive) {
            this.label = label;
            this.archive = archive;
        }

        /**
         * Open an archive held in memory, failing with its label when it is not a readable zip archive (a truncated
         * upload, a blob with the wrong content): the aggregation must not silently skip a per-provider export, or it
         * would publish a Norway dataset missing a whole provider.
         */
        public static Source of(String label, byte[] content) {
            try {
                return new Source(label, ZipFile.builder()
                        .setSeekableByteChannel(new SeekableInMemoryByteChannel(content))
                        .setCharset(StandardCharsets.UTF_8)
                        .get());
            } catch (IOException e) {
                throw new MardukException("The archive " + label + " is not a readable zip archive (" + content.length + " bytes)", e);
            }
        }

        public String label() {
            return label;
        }

        ZipFile archive() {
            return archive;
        }

        @Override
        public void close() throws IOException {
            archive.close();
        }
    }

    /**
     * One entry of the aggregated archive: where to read it from, and the entry to write it under when it is
     * renamed.
     */
    private record Resolved(Source source, ZipArchiveEntry entry, ZipArchiveEntry target) {
    }

    public record Result(int entriesWritten, List<String> duplicateNames, long bytesWritten) {
    }

    /**
     * The entries to write, resolved by name, and the sources they are read from.
     * <p>
     * The plan owns its sources: closing it closes every source that was added to it, whether or not the merge was
     * written. In a try-with-resources block a failure to close a source is added as suppressed to the exception of
     * the merge, rather than replacing it.
     */
    public static final class Plan implements Closeable {

        private final Map<String, Resolved> entries = new LinkedHashMap<>();
        private final List<String> duplicateNames = new ArrayList<>();
        private final List<Source> sources = new ArrayList<>();
        private boolean failOnDuplicateEntry;

        private Plan() {
        }

        public Plan failOnDuplicateEntry(boolean fail) {
            this.failOnDuplicateEntry = fail;
            return this;
        }

        /**
         * Contribute every entry of one archive, under its own name. The plan takes ownership of the source.
         */
        public Plan add(Source source) {
            own(source);
            Enumeration<ZipArchiveEntry> sourceEntries = source.archive.getEntriesInPhysicalOrder();
            while (sourceEntries.hasMoreElements()) {
                ZipArchiveEntry entry = sourceEntries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                put(entry.getName(), source, entry, null);
            }
            return this;
        }

        /**
         * Contribute every top level entry of one archive, renamed {@code prefix + suffix},
         * {@code prefix + 1 + suffix}, ... in physical order. The stop place export is published under names the
         * NeTEx profile mandates.
         * <p>
         * These names are positional, so a nested entry must not shift them. The replaced implementation listed the
         * unpacked folder without recursing and therefore never saw one either.
         */
        public Plan addRenamed(Source source, String prefix, String suffix) {
            own(source);
            int i = 0;
            Enumeration<ZipArchiveEntry> sourceEntries = source.archive.getEntriesInPhysicalOrder();
            while (sourceEntries.hasMoreElements()) {
                ZipArchiveEntry entry = sourceEntries.nextElement();
                if (entry.isDirectory() || entry.getName().indexOf('/') >= 0) {
                    continue;
                }
                String name = prefix + (i > 0 ? i : "") + suffix;
                put(name, source, entry, renamed(entry, name));
                i++;
            }
            return this;
        }

        /**
         * Registered before the entries are read, so that a source whose entries are rejected (an encrypted entry)
         * is still closed with the plan.
         */
        private void own(Source source) {
            sources.add(source);
        }

        private void put(String name, Source source, ZipArchiveEntry entry, ZipArchiveEntry target) {
            if (entry.getGeneralPurposeBit().usesEncryption()) {
                // addRawArchiveEntry rebuilds the general purpose bit flag instead of copying it, so an
                // encrypted entry would silently lose its flag and become unreadable.
                throw new MardukException("Entry " + entry.getName() + " of " + source.label() + " is encrypted "
                        + "and cannot be copied into the aggregated export");
            }
            Resolved previous = this.entries.put(name, new Resolved(source, entry, target));
            if (previous != null) {
                duplicateNames.add(name);
                LOGGER.warn("Entry {} is contributed by both {} and {}: keeping the latter, as the previous "
                                + "implementation did by unpacking every archive into one directory",
                        name, previous.source().label(), source.label());
            }
        }

        /**
         * Write the aggregated archive.
         */
        public Result writeTo(Path targetPath) throws IOException {
            if (failOnDuplicateEntry && !duplicateNames.isEmpty()) {
                throw new MardukException("The aggregated export would contain duplicate entries: " + duplicateNames);
            }
            try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(targetPath.toFile())) {
                out.setEncoding(StandardCharsets.UTF_8.name());
                out.setUseZip64(Zip64Mode.AsNeeded);
                for (Resolved resolved : entries.values()) {
                    // a renamed entry (the stop place export) is written under the planned name
                    ZipArchiveEntry written = resolved.target() != null ? resolved.target() : resolved.entry();
                    try (InputStream raw = resolved.source().archive.getRawInputStream(resolved.entry())) {
                        out.addRawArchiveEntry(written, raw);
                    }
                }
            }
            return new Result(entries.size(), List.copyOf(duplicateNames), Files.size(targetPath));
        }

        /**
         * Close every source, always closing the rest when one fails and reporting the first failure with the others
         * suppressed.
         */
        @Override
        public void close() throws IOException {
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
            sources.clear();
            if (failure != null) {
                throw failure;
            }
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
}

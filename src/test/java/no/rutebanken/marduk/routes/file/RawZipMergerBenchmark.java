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

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Measures the cost of building the aggregated Norway NeTEx export, on real production data.
 * <p>
 * Not a unit test: it needs a few hundred megabytes of real provider exports and several minutes, and the JVM flags
 * are the independent variable (the production pod runs with {@code -Xmx5g -XX:ActiveProcessorCount=2}), which is
 * awkward to control from surefire. It lives under {@code src/test} so that it stays out of the shipped jar while
 * still measuring the production classes rather than a copy of them.
 * <p>
 * Usage:
 * <pre>
 * java -cp target/classes:target/test-classes:$(cat cp.txt) \
 *      no.rutebanken.marduk.routes.file.RawZipMergerBenchmark \
 *      --data &lt;dir&gt; --out &lt;dir&gt; --modes today,raw,raw-dual,today-dual --iterations 3
 * </pre>
 * where {@code <dir>} holds {@code providers/*.zip} and {@code stops.zip}, fetched with
 * {@code gcloud storage cp 'gs://marduk-production/outbound/netex/rb_*-aggregated-netex.zip'}.
 */
public final class RawZipMergerBenchmark {

    private static final String STOPS_PREFIX = "_stops";
    private static final long SPILL_THRESHOLD = Long.MAX_VALUE;

    private RawZipMergerBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = options(args);
        Path data = Path.of(options.getOrDefault("data", "bench-data"));
        Path out = Path.of(options.getOrDefault("out", "bench-out"));
        int iterations = Integer.parseInt(options.getOrDefault("iterations", "3"));
        List<String> modes = List.of(options.getOrDefault("modes", "inventory,today,raw,raw-dual,today-dual").split(","));

        List<Path> providers = providerArchives(data.resolve("providers"));
        Path stops = data.resolve("stops.zip");
        Files.createDirectories(out);

        System.out.printf(Locale.ROOT, "%d provider archives, %d bytes; stops %d bytes%n",
                providers.size(), totalSize(providers), Files.size(stops));

        Map<String, Path> outputs = new LinkedHashMap<>();
        for (String mode : modes) {
            if ("inventory".equals(mode)) {
                inventory(providers, stops);
                continue;
            }
            List<Long> times = new ArrayList<>();
            Path produced = null;
            for (int i = 0; i <= iterations; i++) {
                long gcBefore = gcMillis();
                long start = System.nanoTime();
                produced = run(mode, providers, stops, out);
                long elapsed = (System.nanoTime() - start) / 1_000_000;
                long gc = gcMillis() - gcBefore;
                if (i == 0) {
                    System.out.printf(Locale.ROOT, "%-16s warmup      %6d ms (gc %d ms)%n", mode, elapsed, gc);
                } else {
                    times.add(elapsed);
                    System.out.printf(Locale.ROOT, "%-16s run %d       %6d ms (gc %d ms)%n", mode, i, elapsed, gc);
                }
            }
            times.sort(Comparator.naturalOrder());
            long median = times.get(times.size() / 2);
            System.out.printf(Locale.ROOT, "%-16s MEDIAN      %6d ms   min %6d ms   out %,d bytes  %d entries%n",
                    mode, median, times.getFirst(), Files.size(produced), entryCount(produced));
            outputs.put(mode, produced);
        }

        if (outputs.containsKey("today")) {
            for (Map.Entry<String, Path> candidate : outputs.entrySet()) {
                if (!"today".equals(candidate.getKey())) {
                    verify(outputs.get("today"), candidate.getValue(), candidate.getKey());
                }
            }
        }
    }

    private static Path run(String mode, List<Path> providers, Path stops, Path out) throws IOException {
        return switch (mode) {
            case "today" -> today(providers, stops, out, 1).getFirst();
            case "today-dual" -> today(providers, stops, out, 2).getFirst();
            case "raw" -> raw(providers, stops, out, 1).getFirst();
            case "raw-dual" -> raw(providers, stops, out, 2).getFirst();
            default -> throw new IllegalArgumentException("unknown mode " + mode);
        };
    }

    /**
     * The production algorithm: unpack every archive into one flat directory, copy and rename the stop files into it,
     * then deflate the whole directory. Repeated once per variant, as the dual export does.
     */
    private static List<Path> today(List<Path> providers, Path stops, Path out, int variants) throws IOException {
        List<Path> results = new ArrayList<>();
        Path stopsDir = out.resolve("today-stops");
        FileUtils.deleteDirectory(stopsDir.toFile());
        Files.createDirectories(stopsDir);
        try (var in = Files.newInputStream(stops)) {
            ZipFileUtils.unzipFile(in, stopsDir.toString());
        }
        for (int variant = 0; variant < variants; variant++) {
            Path unpacked = out.resolve("today-unpacked");
            FileUtils.deleteDirectory(unpacked.toFile());
            Files.createDirectories(unpacked);
            for (Path provider : providers) {
                try (var in = Files.newInputStream(provider)) {
                    ZipFileUtils.unzipFile(in, unpacked.toString());
                }
            }
            int i = 0;
            for (File stopFile : FileUtils.listFiles(stopsDir.toFile(), null, false)) {
                FileUtils.copyFile(stopFile, new File(unpacked.toFile(), STOPS_PREFIX + (i > 0 ? i : "") + ".xml"));
                i++;
            }
            Path result = out.resolve("today-merged-" + variant + ".zip");
            Files.deleteIfExists(result);
            ZipFileUtils.zipFilesInFolder(unpacked.toString(), result.toString());
            FileUtils.deleteDirectory(unpacked.toFile());
            results.add(result);
        }
        FileUtils.deleteDirectory(stopsDir.toFile());
        return results;
    }

    /**
     * The raw copy: every provider archive contributes its already-deflated entries to every output in one pass.
     * With two outputs this is the dual export built from sources that are identical between the two variants, which
     * is what 59 of the 62 providers look like in production.
     */
    private static List<Path> raw(List<Path> providers, Path stops, Path out, int variants) throws IOException {
        List<Path> results = new ArrayList<>();
        for (int variant = 0; variant < variants; variant++) {
            results.add(out.resolve("raw-merged-" + variant + ".zip"));
            Files.deleteIfExists(results.get(variant));
        }
        List<RawZipMerger.Source> sources = new ArrayList<>();
        try {
            RawZipMerger.Plan plan = RawZipMerger.plan();
            for (Path provider : providers) {
                RawZipMerger.Source source = RawZipMerger.Source.of(
                        provider.getFileName().toString(), Files.readAllBytes(provider), SPILL_THRESHOLD, out);
                sources.add(source);
                plan.add(source);
            }
            RawZipMerger.Source stopsSource = RawZipMerger.Source.of(
                    "stops.zip", Files.readAllBytes(stops), SPILL_THRESHOLD, out);
            sources.add(stopsSource);
            plan.addRenamed(stopsSource, STOPS_PREFIX, ".xml");
            RawZipMerger.Result result = plan.writeTo(results);
            if (!result.duplicateNames().isEmpty()) {
                System.out.println("  duplicate entry names: " + result.duplicateNames());
            }
        } finally {
            RawZipMerger.closeAll(sources);
        }
        return results;
    }

    /**
     * Reads only the central directories, to find out whether the provider archives contribute colliding entry
     * names. The production code unpacks them all into one directory, where a collision is silently resolved by the
     * last writer, so a collision would mean the published archive is already losing a file.
     */
    private static void inventory(List<Path> providers, Path stops) throws IOException {
        Map<String, List<String>> owners = new TreeMap<>();
        long entries = 0;
        long uncompressed = 0;
        for (Path provider : providers) {
            try (ZipFile zip = new ZipFile(provider.toFile())) {
                for (ZipEntry entry : zip.stream().toList()) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    entries++;
                    uncompressed += entry.getSize();
                    owners.computeIfAbsent(entry.getName(), n -> new ArrayList<>())
                            .add(provider.getFileName().toString());
                }
            }
        }
        try (ZipFile zip = new ZipFile(stops.toFile())) {
            System.out.printf(Locale.ROOT, "stops archive: %d entries, %,d bytes uncompressed%n",
                    zip.size(), zip.stream().mapToLong(ZipEntry::getSize).sum());
        }
        List<String> duplicates = owners.entrySet().stream().filter(e -> e.getValue().size() > 1)
                .map(e -> e.getKey() + " " + e.getValue()).toList();
        System.out.printf(Locale.ROOT,
                "inventory: %d entries, %d distinct names, %,d bytes uncompressed, %d duplicate name(s)%n",
                entries, owners.size(), uncompressed, duplicates.size());
        duplicates.forEach(d -> System.out.println("  DUPLICATE " + d));
    }

    /**
     * Content equality: same entry names, and for each the same uncompressed size and CRC. That is equality of the
     * decompressed bytes up to a CRC32 collision, at no inflation cost.
     */
    private static void verify(Path expected, Path actual, String label) throws IOException {
        Map<String, long[]> left = index(expected);
        Map<String, long[]> right = index(actual);
        var onlyLeft = new TreeSet<>(left.keySet());
        onlyLeft.removeAll(right.keySet());
        var onlyRight = new TreeSet<>(right.keySet());
        onlyRight.removeAll(left.keySet());
        List<String> differing = left.entrySet().stream()
                .filter(e -> right.containsKey(e.getKey()))
                .filter(e -> e.getValue()[0] != right.get(e.getKey())[0]
                        || e.getValue()[1] != right.get(e.getKey())[1])
                .map(Map.Entry::getKey)
                .toList();
        if (onlyLeft.isEmpty() && onlyRight.isEmpty() && differing.isEmpty()) {
            System.out.printf(Locale.ROOT, "VERIFY %-12s OK: %d entries identical (name, size, crc)%n",
                    label, left.size());
        } else {
            System.out.printf(Locale.ROOT, "VERIFY %-12s FAILED: %d only in baseline %s, %d only in candidate %s, "
                            + "%d differing %s%n",
                    label, onlyLeft.size(), head(onlyLeft), onlyRight.size(), head(onlyRight),
                    differing.size(), head(differing));
            throw new IllegalStateException("the aggregated archives are not content-equal");
        }
    }

    private static Map<String, long[]> index(Path archive) throws IOException {
        Map<String, long[]> index = new TreeMap<>();
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            for (ZipEntry entry : zip.stream().toList()) {
                if (!entry.isDirectory()) {
                    index.put(entry.getName(), new long[]{entry.getSize(), entry.getCrc()});
                }
            }
        }
        return index;
    }

    private static Object head(java.util.Collection<String> names) {
        return names.stream().limit(5).toList();
    }

    private static int entryCount(Path archive) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            return zip.size();
        }
    }

    private static List<Path> providerArchives(Path dir) throws IOException {
        try (var files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".zip")).sorted().toList();
        }
    }

    private static long totalSize(List<Path> paths) throws IOException {
        long total = 0;
        for (Path path : paths) {
            total += Files.size(path);
        }
        return total;
    }

    private static long gcMillis() {
        long total = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            total += bean.getCollectionTime();
        }
        return total;
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            options.put(args[i].replaceFirst("^--", ""), args[i + 1]);
        }
        return options;
    }
}

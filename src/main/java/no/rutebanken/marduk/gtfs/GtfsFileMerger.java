package no.rutebanken.marduk.gtfs;

import no.rutebanken.marduk.exceptions.MardukException;
import org.zeroturnaround.zip.ZipUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GtfsFileMerger {

    private static final String[] GTFS_FILE_NAMES = new String[]{"agency.txt", "calendar.txt", "calendar_dates.txt", "routes.txt", "shapes.txt", "stops.txt", "stop_times.txt", "trips.txt", "transfers.txt"};

    private Set<String> stopIds = new HashSet<>();
    private Path workingDirectory;

    public GtfsFileMerger(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public void appendGtfs(File gtfsFile) {
        ZipUtil.iterate(gtfsFile, GTFS_FILE_NAMES, (entryStream, zipEntry) -> {
            String entryName = zipEntry.getName();
            Path destinationFile = workingDirectory.resolve(entryName);
            boolean ignoreHeader = Files.exists(destinationFile);

            if ("stops.txt".equals(entryName)) {
                appendStopEntry(entryStream, destinationFile, ignoreHeader);
            } else {
                appendEntry(entryName, entryStream, destinationFile, ignoreHeader);
            }
        });
    }

    private void appendStopEntry(InputStream entryStream, Path destinationFile, boolean ignoreHeader) {
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(entryStream, StandardCharsets.UTF_8));
             BufferedWriter writer = Files.newBufferedWriter(destinationFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            String header = bufferedReader.readLine();
            if (!ignoreHeader) {
                copyLine(writer, header);
            }
            // copy all remaining lines
            bufferedReader.lines().forEach(line -> {
                String stopId = line.substring(0, line.indexOf(','));
                if (!stopIds.contains(stopId)) {
                    stopIds.add(stopId);
                    copyLine(writer, line);
                }
            });
        } catch (IOException e) {
            throw new MardukException(e);
        }
    }

    private void appendEntry(String entryName, InputStream entryStream, Path destinationFile, boolean ignoreHeader) {
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(entryStream, StandardCharsets.UTF_8));
             BufferedWriter writer = Files.newBufferedWriter(destinationFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {

            String[] sourceHeadersForEntry = bufferedReader.readLine().split(",");
            String[] targetHeadersForEntry = getTargetHeader(entryName);
            GtfsLineBuilder gtfsLineBuilder = new GtfsLineBuilder(sourceHeadersForEntry, targetHeadersForEntry);
            if (!ignoreHeader) {
                copyLine(writer, Arrays.asList(targetHeadersForEntry).stream().collect(Collectors.joining(",")));
            }
            // copy all remaining lines
            bufferedReader.lines().forEach(line -> {
                copyLine(writer, gtfsLineBuilder.getLine(line));
            });
        } catch (IOException e) {
            throw new MardukException(e);
        }
    }

    private String[] getTargetHeader(String entryName) {
       return GtfsHeaders.GTFS_EXTENDED_HEADERS.get(entryName);
    }

    private void copyLine(BufferedWriter writer, String line) {
        try {
            writer.write(line);
            writer.newLine();
        } catch (IOException e) {
            throw new MardukException(e);
        }
    }
}

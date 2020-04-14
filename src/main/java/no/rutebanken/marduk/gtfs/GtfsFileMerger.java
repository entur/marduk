package no.rutebanken.marduk.gtfs;

import no.rutebanken.marduk.exceptions.MardukException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Merge a collection of GTFS archives into a single zip.
 * Duplicates in stops.txt and transfers.txt are removed
 * All other GTFS entries are assumed to not overlap.
 * Stops duplicates are identiifed by stop/quay id
 * Transfers duplicates are identified by comparing hashcode of the whole CSV line.
 */
public class GtfsFileMerger {

    private static final String[] GTFS_FILE_NAMES = new String[]{"agency.txt", "calendar.txt", "calendar_dates.txt", "routes.txt", "shapes.txt", "stops.txt", "stop_times.txt", "trips.txt", "transfers.txt"};


    private static Logger LOGGER = LoggerFactory.getLogger(GtfsFileMerger.class);

    private Path workingDirectory;
    private Map<String,String[]> targetGtfsHeaders;

    private Set<String> stopIds = new HashSet<>();
    private Set<Integer> transferHashes = new HashSet<>();


    /**
     * @param workingDirectory temporary directory where the GTFS files are merged.
     * @param gtfsExport       the type of GTFS export.
     */
    public GtfsFileMerger(Path workingDirectory, GtfsExport gtfsExport) {
        this.workingDirectory = workingDirectory;
        this.targetGtfsHeaders = gtfsExport.getHeaders();
    }

    /**
     * Merge a GTFS file into the working directory.
     *
     * @param gtfsFile
     */
    public void appendGtfs(File gtfsFile) {

        LOGGER.debug("Merging file {}", gtfsFile.getName());

        ZipUtil.iterate(gtfsFile, GTFS_FILE_NAMES, (entryStream, zipEntry) -> {
            String entryName = zipEntry.getName();
            Path destinationFile = workingDirectory.resolve(entryName);
            boolean ignoreHeader = Files.exists(destinationFile);

            if ("stops.txt".equals(entryName)) {
                appendStopEntry(entryStream, destinationFile, ignoreHeader);
            } else if ("transfers.txt".equals(entryName)) {
                appendTransferEntry(entryStream, destinationFile, ignoreHeader);
            } else {
                appendEntry(entryName, entryStream, destinationFile, ignoreHeader);
            }
        });
    }

    /**
     * Append stop entries and remove duplicates.
     *
     * @param entryStream
     * @param destinationFile
     * @param ignoreHeader
     */
    private void appendStopEntry(InputStream entryStream, Path destinationFile, boolean ignoreHeader) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(entryStream, StandardCharsets.UTF_8));
             BufferedWriter writer = Files.newBufferedWriter(destinationFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {

            CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader()
                    .withIgnoreHeaderCase()
                    .withTrim());

            String[] targetHeaders = getTargetHeaders("stops.txt");

            CSVPrinter csvPrinter = ignoreHeader
                    ? new CSVPrinter(writer, CSVFormat.DEFAULT)
                    : new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(targetHeaders));

            for (CSVRecord csvRecord : csvParser) {
                String stopId = csvRecord.get("stop_id");
                if (!stopIds.contains(stopId)) {
                    stopIds.add(stopId);
                    List<String> targetValues = Stream.of(targetHeaders).map(header -> convertValue(csvRecord, header)).collect(Collectors.toList());
                    csvPrinter.printRecord(targetValues);
                }
            }
            csvPrinter.flush();
        } catch (IOException e) {
            throw new MardukException(e);
        }
    }

    /**
     * Append transfer entries and remove duplicates.
     *
     * @param entryStream
     * @param destinationFile
     * @param ignoreHeader
     */
    private void appendTransferEntry(InputStream entryStream, Path destinationFile, boolean ignoreHeader) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(entryStream, StandardCharsets.UTF_8));
             BufferedWriter writer = Files.newBufferedWriter(destinationFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {

            CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader()
                    .withIgnoreHeaderCase()
                    .withTrim());

            String[] targetHeaders = getTargetHeaders("transfers.txt");

            CSVPrinter csvPrinter = ignoreHeader
                    ? new CSVPrinter(writer, CSVFormat.DEFAULT)
                    : new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(targetHeaders));

            for (CSVRecord csvRecord : csvParser) {
                List<String> targetValues = Stream.of(targetHeaders).map(header -> convertValue(csvRecord, header)).collect(Collectors.toList());
                int transferHash = targetValues.hashCode();
                if (!transferHashes.contains(transferHash)) {
                    transferHashes.add(transferHash);
                    csvPrinter.printRecord(targetValues);
                }
            }
            csvPrinter.flush();
        } catch (IOException e) {
            throw new MardukException(e);
        }
    }

    /**
     * Append GTFS entries other than stops and transfers. No duplicate check is performed.
     *
     * @param entryName
     * @param entryStream
     * @param destinationFile
     * @param ignoreHeader
     */
    private void appendEntry(String entryName, InputStream entryStream, Path destinationFile, boolean ignoreHeader) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(entryStream, StandardCharsets.UTF_8));
             BufferedWriter writer = Files.newBufferedWriter(destinationFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {

            CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader()
                    .withIgnoreHeaderCase()
                    .withTrim());

            String[] targetHeaders = getTargetHeaders(entryName);

            CSVPrinter csvPrinter = ignoreHeader
                    ? new CSVPrinter(writer, CSVFormat.DEFAULT)
                    : new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(targetHeaders));

            for (CSVRecord csvRecord : csvParser) {
                List<String> targetValues = Stream.of(targetHeaders).map(header -> convertValue(csvRecord, header)).collect(Collectors.toList());
                csvPrinter.printRecord(targetValues);
            }
            csvPrinter.flush();
        } catch (IOException e) {
            throw new MardukException(e);
        }
    }

    private String convertValue(CSVRecord csvRecord, String header) {
        if(!csvRecord.isSet(header)) {
            return "";
        }
        String value = csvRecord.get(header);
        if ("wheelchair_accessible".equals(header) && "0".equals(value)) {
            return "";
        }
        if ("location_type".equals(header) && "0".equals(value)) {
            return "";
        }
        if ("drop_off_type".equals(header) && "0".equals(value)) {
            return "";
        }
        if ("pickup_type".equals(header) && "0".equals(value)) {
            return "";
        }

        return value;
    }

    private String[] getTargetHeaders(String entryName) {
        return targetGtfsHeaders.get(entryName);
    }

}

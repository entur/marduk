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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static no.rutebanken.marduk.gtfs.GtfsExport.GTFS_EXTENDED;
import static no.rutebanken.marduk.gtfs.GtfsExport.GTFS_GOOGLE;

/**
 * Merge a collection of GTFS archives into a single zip.
 * Duplicates in stops.txt and transfers.txt are removed.
 * All other GTFS entries are assumed to not overlap.
 * Stops duplicates are identified by stop/quay id.
 * Transfers duplicates are identified by string-equality on the whole CSV line.
 */
public class GtfsFileMerger {

    private static final String[] GTFS_FILE_NAMES = new String[]{GtfsConstants.AGENCY_TXT, GtfsConstants.CALENDAR_TXT,
            GtfsConstants.CALENDAR_DATES_TXT, GtfsConstants.ROUTES_TXT, GtfsConstants.SHAPES_TXT,
            GtfsConstants.STOPS_TXT, GtfsConstants.STOP_TIMES_TXT, GtfsConstants.TRIPS_TXT, GtfsConstants.TRANSFERS_TXT};

    private static final Logger LOGGER = LoggerFactory.getLogger(GtfsFileMerger.class);

    private Path workingDirectory;
    private GtfsExport gtfsExport;
    private boolean includeShapes;

    private Set<String> stopIds = new HashSet<>(150000);
    private Set<List<String>> transfers = new HashSet<>(15000);


    /**
     * @param workingDirectory temporary directory in which the GTFS files are merged.
     * @param gtfsExport       the type of GTFS export.
     */
    public GtfsFileMerger(Path workingDirectory, GtfsExport gtfsExport, boolean includeShapes) {
        this.workingDirectory = workingDirectory;
        this.gtfsExport = gtfsExport;
        this.includeShapes = includeShapes;
    }

    /**
     * Merge a GTFS file into the working directory.
     *
     * @param gtfsFile the current GTFS archive being merged.
     */
    public void appendGtfs(File gtfsFile) {

        LOGGER.debug("Merging file {}", gtfsFile.getName());

        ZipUtil.iterate(gtfsFile, GTFS_FILE_NAMES, (entryStream, zipEntry) -> {
            String entryName = zipEntry.getName();
            Path destinationFile = workingDirectory.resolve(entryName);
            boolean ignoreHeader = Files.exists(destinationFile);

            if (GtfsConstants.STOPS_TXT.equals(entryName)) {
                appendStopEntry(entryStream, destinationFile, ignoreHeader);
            } else if (GtfsConstants.TRANSFERS_TXT.equals(entryName)) {
                appendTransferEntry(entryStream, destinationFile, ignoreHeader);
            } else if (GtfsConstants.SHAPES_TXT.equals(entryName) && ! includeShapes) {
                LOGGER.trace("Ignoring shapes data in GTFS file {}", gtfsFile.getName());
            } else {
                appendEntry(entryName, entryStream, destinationFile, ignoreHeader);
            }
        });
    }

    /**
     * Append stop entries and remove duplicates.
     *
     * @param entryStream     the GTFS file entry inside the GTFS archive.
     * @param destinationFile the temporary file where GTFS lines are merged.
     * @param ignoreHeader    ignore headers. Headers are created only when the destination file is first created.
     */
    private void appendStopEntry(InputStream entryStream, Path destinationFile, boolean ignoreHeader) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(entryStream, StandardCharsets.UTF_8));
             BufferedWriter writer = Files.newBufferedWriter(destinationFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {

            CSVParser csvParser = new CSVParser(reader, getCsvFormatWithFirstRecordHasHeaders());

            String[] targetHeaders = getTargetHeaders(GtfsConstants.STOPS_TXT);

            CSVPrinter csvPrinter = ignoreHeader
                    ? new CSVPrinter(writer, CSVFormat.DEFAULT)
                    : new CSVPrinter(writer, getCsvFormatWithHeaders(targetHeaders));

            for (CSVRecord csvRecord : csvParser) {
                String stopId = csvRecord.get("stop_id");
                if (!stopIds.contains(stopId)) {
                    stopIds.add(stopId);
                    List<String> targetValues = Stream.of(targetHeaders).map(header -> convertValue(csvRecord, header)).collect(Collectors.toList());
                    csvPrinter.printRecord(targetValues);
                } else {
                    LOGGER.trace("Ignored duplicated stop: {}", stopId);
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
     * @param entryStream     the GTFS file entry inside the GTFS archive.
     * @param destinationFile the temporary file where GTFS lines are merged.
     * @param ignoreHeader    ignore headers. Headers are created only when the destination file is first created.
     */
    private void appendTransferEntry(InputStream entryStream, Path destinationFile, boolean ignoreHeader) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(entryStream, StandardCharsets.UTF_8));
             BufferedWriter writer = Files.newBufferedWriter(destinationFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {

            CSVParser csvParser = new CSVParser(reader, getCsvFormatWithFirstRecordHasHeaders());

            String[] targetHeaders = getTargetHeaders(GtfsConstants.TRANSFERS_TXT);

            CSVPrinter csvPrinter = ignoreHeader
                    ? new CSVPrinter(writer, CSVFormat.DEFAULT)
                    : new CSVPrinter(writer, getCsvFormatWithHeaders(targetHeaders));

            for (CSVRecord csvRecord : csvParser) {
                List<String> targetValues = Stream.of(targetHeaders).map(header -> convertValue(csvRecord, header)).collect(Collectors.toList());
                if (!transfers.contains(targetValues)) {
                    transfers.add(targetValues);
                    csvPrinter.printRecord(targetValues);
                } else {
                    LOGGER.trace("Ignored duplicated transfer: {}", targetValues);
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
     * @param entryName       the GTFS file entry name inside the GTFS archive.
     * @param entryStream     the GTFS file entry inside the GTFS archive.
     * @param destinationFile the temporary file where GTFS lines are merged.
     * @param ignoreHeader    ignore headers. Headers are created only when the destination file is first created.
     */
    private void appendEntry(String entryName, InputStream entryStream, Path destinationFile, boolean ignoreHeader) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(entryStream, StandardCharsets.UTF_8));
             BufferedWriter writer = Files.newBufferedWriter(destinationFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {

            CSVParser csvParser = new CSVParser(reader, getCsvFormatWithFirstRecordHasHeaders());

            String[] targetHeaders = getTargetHeaders(entryName);

            CSVPrinter csvPrinter = ignoreHeader
                    ? new CSVPrinter(writer, CSVFormat.DEFAULT)
                    : new CSVPrinter(writer, getCsvFormatWithHeaders(targetHeaders));

            for (CSVRecord csvRecord : csvParser) {
                List<String> targetValues = Stream.of(targetHeaders).map(header -> convertValue(csvRecord, header)).collect(Collectors.toList());
                csvPrinter.printRecord(targetValues);
            }
            csvPrinter.flush();
        } catch (IOException e) {
            throw new MardukException(e);
        }
    }

    /**
     * Convert default values. null values are inserted as empty string, as well as some default 0 values that are
     * converted into empty string for compatibility with the original merge algorithm.
     *
     * @param csvRecord
     * @param header
     * @return
     */
    private String convertValue(CSVRecord csvRecord, String header) {
        if (!csvRecord.isSet(header)) {
            return "";
        }
        String value = csvRecord.get(header);
        if (value.isEmpty()) {
            return "";
        }
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
        if ("route_type".equals(header) || "vehicle_type".equals(header)) {
            if (gtfsExport == GTFS_EXTENDED) {
                return value;
            }
            int routeTypeCode = 0;
            try {
                routeTypeCode = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid route type {}", value);
                return value;
            }
            if (gtfsExport == GTFS_GOOGLE) {
                return Integer.toString(GoogleRouteTypeCode.toGoogleSupportedRouteTypeCode(routeTypeCode));
            } else {
                return Integer.toString(BasicRouteTypeCode.convertRouteType(routeTypeCode));
            }
        }

        if ("shape_id".equals(header) && ! includeShapes) {
            return "";
        }

        return value;
    }

    private String[] getTargetHeaders(String entryName) {
        return gtfsExport.getHeaders().get(entryName);
    }

    private CSVFormat getCsvFormatWithFirstRecordHasHeaders() {
        return CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build();
    }

    private CSVFormat getCsvFormatWithHeaders(String[] targetHeaders) {
        return CSVFormat.DEFAULT.builder().setHeader(targetHeaders).build();
    }
}

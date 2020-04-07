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

package no.rutebanken.marduk.gtfs;

import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.routes.file.TempFileUtils;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.onebusaway.gtfs.serialization.GtfsEntitySchemaFactory;
import org.onebusaway.gtfs_merge.GtfsMerger;
import org.onebusaway.gtfs_merge.strategies.AbstractEntityMergeStrategy;
import org.onebusaway.gtfs_merge.strategies.EDuplicateDetectionStrategy;
import org.onebusaway.gtfs_merge.strategies.EntityMergeStrategy;
import org.onebusaway.gtfs_transformer.factory.EntitiesTransformStrategy;
import org.onebusaway.gtfs_transformer.match.AlwaysMatch;
import org.onebusaway.gtfs_transformer.match.TypedEntityMatch;
import org.onebusaway.gtfs_transformer.services.EntityTransformStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.zip.ByteSource;
import org.zeroturnaround.zip.ZipEntrySource;
import org.zeroturnaround.zip.ZipUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class GtfsFileUtils {
    private static Logger logger = LoggerFactory.getLogger(GtfsFileUtils.class);

    private static final String[] GTFS_FILE_NAMES = new String[] {"agency.txt", "calendar.txt", "calendar_dates.txt", "routes.txt", "shapes.txt", "stops.txt", "stop_times.txt", "trips.txt", "transfers.txt"};

    public static final String FEED_INFO_FILE_NAME = "feed_info.txt";
    private static final byte[] FEED_INFO_FILE_CONTENT = "feed_id,feed_publisher_name,feed_publisher_url,feed_lang\nENTUR,Entur,https://www.entur.org,no".getBytes(StandardCharsets.UTF_8);

    public static InputStream mergeGtfsFilesInDirectory(String sourceDirectory) {

        Collection<File> zipFiles = FileUtils.listFiles(new File(sourceDirectory), new String[]{"zip"}, false);
        zipFiles.forEach(GtfsFileUtils::appendGtfs);

        String targetDirectory = Path.of(sourceDirectory).resolve("merged").toString();
        try {
            IOUtils.write(FEED_INFO_FILE_CONTENT, new FileOutputStream(Path.of(targetDirectory).resolve(FEED_INFO_FILE_NAME).toFile()));
        } catch (IOException e) {
            throw new MardukException(e);
        }

        File targetFile = Path.of(sourceDirectory).resolve("merged.zip").toFile();
        ZipFileUtils.zipFilesInFolder(targetDirectory, targetFile.toString());
        try {
            return TempFileUtils.createDeleteOnCloseInputStream(targetFile);
        } catch (IOException e) {
            throw new MardukException(e);
        }

    }

    private static void appendGtfs(File file) {
        ZipUtil.iterate(file, GTFS_FILE_NAMES, (entryStream, zipEntry) -> {
            Path destinationPath = file.toPath().getParent().resolve("merged");
            if (!Files.exists(destinationPath)) {
                Files.createDirectory(destinationPath);
            }

            String entryName = zipEntry.getName();
            Path destinationFile = destinationPath.resolve(entryName);
            boolean ignoreHeader = Files.exists(destinationFile);

            if("stops.txt".equals(entryName)) {
                appendStopEntry(entryStream, destinationFile, ignoreHeader);
            } else {
                appendEntry(entryStream, destinationFile, ignoreHeader);
            }


        });

    }

    private static Set<String> stopIds = new HashSet<>();

    private static void appendStopEntry(InputStream entryStream, Path destinationFile, boolean ignoreHeader) {
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(entryStream, StandardCharsets.UTF_8));
             BufferedWriter writer = Files.newBufferedWriter(destinationFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            String header = bufferedReader.readLine();
            if (! ignoreHeader) {
                copyLine(writer, header);
            }
            // copy all remaining lines
            bufferedReader.lines().forEach(line -> {
                String stopId = line.substring(0, line.indexOf(','));
                if(! stopIds.contains(stopId)) {
                    stopIds.add(stopId);
                    copyLine(writer, line);
                }

            });
        } catch (IOException e) {
            throw new MardukException(e);
        }
    }

    private static void copyLine(BufferedWriter writer, String line) {
        try {
            writer.write(line);
            writer.newLine();

        } catch (IOException e) {
            throw new MardukException(e);
        }
    }

    private static void appendEntry(InputStream entryStream, Path destinationFile, boolean ignoreHeader) {
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(entryStream, StandardCharsets.UTF_8));
             BufferedWriter writer = Files.newBufferedWriter(destinationFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (ignoreHeader) {
                bufferedReader.readLine();
            }
            // copy all remaining lines
            bufferedReader.lines().forEach(line -> {
                copyLine(writer, line);
            });
        } catch (IOException e) {
            throw new MardukException(e);
        }
    }

    static File mergeGtfsFiles(Collection<File> files) {

        try {
            long t1 = System.currentTimeMillis();
            logger.debug("Merging GTFS-files");

            File outputFile = File.createTempFile("marduk-merge-gtfs-", ".zip");
            buildGtfsMerger(EDuplicateDetectionStrategy.IDENTITY).run(new ArrayList<>(files), outputFile);

            addOrReplaceFeedInfo(outputFile);

            logger.debug("Merged GTFS-files - spent {} ms", (System.currentTimeMillis() - t1));

            return outputFile;

        } catch (IOException ioException) {
            throw new MardukException("Merging of GTFS files failed", ioException);
        }

    }

    public static EntitiesTransformStrategy createEntitiesTransformStrategy(Class<?> entityClass, EntityTransformStrategy strategy) {
        EntitiesTransformStrategy transformStrategy = new EntitiesTransformStrategy();
        transformStrategy.addModification(new TypedEntityMatch(entityClass, new AlwaysMatch()), strategy);
        return transformStrategy;
    }

    private static GtfsMerger buildGtfsMerger(EDuplicateDetectionStrategy duplicateDetectionStrategy) {
        GtfsMerger merger = new GtfsMerger();

        merger.setTransferStrategy(new ExtendedTransferMergeStrategy());
        for (Class<?> entityClass : GtfsEntitySchemaFactory.getEntityClasses()) {
            EntityMergeStrategy entityMergeStrategy = merger.getEntityMergeStrategyForEntityType(entityClass);
            if (entityMergeStrategy instanceof AbstractEntityMergeStrategy) {
                ((AbstractEntityMergeStrategy) entityMergeStrategy).setDuplicateDetectionStrategy(duplicateDetectionStrategy);
            }

/*            if (entityMergeStrategy instanceof StopMergeStrategy) {
                ((StopMergeStrategy) entityMergeStrategy).setDuplicateDetectionStrategy(EDuplicateDetectionStrategy.IDENTITY);
            } else if (entityMergeStrategy instanceof AbstractEntityMergeStrategy) {
                ((AbstractEntityMergeStrategy) entityMergeStrategy).setDuplicateDetectionStrategy(duplicateDetectionStrategy);
            }*/

        }

        return merger;
    }

    public static void addOrReplaceFeedInfo(File gtfsZipFile) {
        ZipEntrySource feedInfoEntry = new ByteSource(FEED_INFO_FILE_NAME, FEED_INFO_FILE_CONTENT);
        ZipUtil.addOrReplaceEntries(gtfsZipFile, new ZipEntrySource[]{feedInfoEntry});
    }

    public static InputStream addOrReplaceFeedInfo(InputStream source) throws IOException {
        File tmpZip = File.createTempFile("marduk-add-or-replace-feed-info-", ".zip");
        Files.copy(source, tmpZip.toPath(), StandardCopyOption.REPLACE_EXISTING);
        addOrReplaceFeedInfo(tmpZip);
        return TempFileUtils.createDeleteOnCloseInputStream(tmpZip);
    }
}
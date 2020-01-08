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
import org.apache.commons.io.FileUtils;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

public class GtfsFileUtils {
    private static Logger logger = LoggerFactory.getLogger(GtfsFileUtils.class);

    public static final String FEED_INFO_FILE_NAME = "feed_info.txt";

    public static File mergeGtfsFilesInDirectory(String path) {
        return mergeGtfsFiles(FileUtils.listFiles(new File(path), new String[]{"zip"}, false));
    }


    public static File mergeGtfsFiles(Collection<File> files) {

        try {
            long t1 = System.currentTimeMillis();
            logger.debug("Merging GTFS-files");

            File outputFile = File.createTempFile("marduk-merge", ".zip");
            buildGtfsMerger(EDuplicateDetectionStrategy.IDENTITY).run(new ArrayList<>(files), outputFile);

            addFeedInfoFromFirstGtfsFile(files, outputFile);

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

    private static void addFeedInfoFromFirstGtfsFile(Collection<File> files, File outputFile) throws IOException {
        ByteArrayOutputStream feedInfoStream = extractFeedInfoFile(files);
        addFeedInfoToArchive(outputFile, feedInfoStream);
    }

    private static void addFeedInfoToArchive(File outputFile, ByteArrayOutputStream feedInfoStream) throws IOException {
        if (feedInfoStream != null) {
            File tmp = new File(FEED_INFO_FILE_NAME);
            feedInfoStream.writeTo(new FileOutputStream(tmp));
            FileUtils.copyInputStreamToFile(ZipFileUtils.addFilesToZip(new FileInputStream(outputFile), tmp), outputFile);
            tmp.delete();
        }
    }


    private static GtfsMerger buildGtfsMerger(EDuplicateDetectionStrategy duplicateDetectionStrategy) {
        GtfsMerger merger = new GtfsMerger();

        merger.setTransferStrategy(new ExtendedTransferMergeStrategy());
        for (Class<?> entityClass : GtfsEntitySchemaFactory.getEntityClasses()) {
            EntityMergeStrategy entityMergeStrategy = merger.getEntityMergeStrategyForEntityType(entityClass);
            if (entityMergeStrategy instanceof AbstractEntityMergeStrategy) {
                ((AbstractEntityMergeStrategy) entityMergeStrategy).setDuplicateDetectionStrategy(duplicateDetectionStrategy);
            }
        }
        return merger;
    }


    private static ByteArrayOutputStream extractFeedInfoFile(Collection<File> files) throws IOException {
        ZipFileUtils zipFileUtils = new ZipFileUtils();
        for (File file : files) {
            if (zipFileUtils.listFilesInZip(file).stream().anyMatch(f -> FEED_INFO_FILE_NAME.equals(f))) {
                return zipFileUtils.extractFileFromZipFile(new FileInputStream(file), FEED_INFO_FILE_NAME);
            }

        }
        return null;
    }

}

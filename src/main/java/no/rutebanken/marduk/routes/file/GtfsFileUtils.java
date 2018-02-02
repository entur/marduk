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
import no.rutebanken.marduk.routes.file.beans.CustomGtfsFileTransformer;
import org.apache.commons.io.FileUtils;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.IdentityBean;
import org.onebusaway.gtfs.model.ServiceCalendar;
import org.onebusaway.gtfs.model.ServiceCalendarDate;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.Transfer;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.serialization.GtfsEntitySchemaFactory;
import org.onebusaway.gtfs.services.GtfsMutableRelationalDao;
import org.onebusaway.gtfs_merge.GtfsMerger;
import org.onebusaway.gtfs_merge.strategies.AbstractEntityMergeStrategy;
import org.onebusaway.gtfs_merge.strategies.EDuplicateDetectionStrategy;
import org.onebusaway.gtfs_merge.strategies.EntityMergeStrategy;
import org.onebusaway.gtfs_transformer.GtfsTransformer;
import org.onebusaway.gtfs_transformer.factory.EntitiesTransformStrategy;
import org.onebusaway.gtfs_transformer.match.AlwaysMatch;
import org.onebusaway.gtfs_transformer.match.TypedEntityMatch;
import org.onebusaway.gtfs_transformer.services.EntityTransformStrategy;
import org.onebusaway.gtfs_transformer.services.TransformContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

public class GtfsFileUtils {
    private static Logger logger = LoggerFactory.getLogger(GtfsFileUtils.class);

    public static final String FEED_INFO_FILE_NAME = "feed_info.txt";

    private static final CustomGtfsFileTransformer IDS_TOP_OTP_FORMAT_TRANSFORMER = new CustomGtfsFileTransformer() {

        @Override
        protected void addCustomTransformations(GtfsTransformer transformer) {
            GtfsEntitySchemaFactory.getEntityClasses()
                    .forEach(ec -> transformer.addTransform(createEntitiesTransformStrategy(ec, new IdSeparatorTransformer())));
        }
    };

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

    /**
     * OTP requires ids with '.' as separator instead of ':'.
     * <p>
     * Create a copy of a GTFS file with all ids transformed to replace id separator chars.
     */
    public static File transformIdsToOTPFormat(File inputFile) throws Exception {

        logger.debug("Replacing id separator in inputfile: " + inputFile.getPath());
        long t1 = System.currentTimeMillis();


        File outputFile = IDS_TOP_OTP_FORMAT_TRANSFORMER.transform(inputFile);

        logger.debug("Replaced id separator in GTFS-file - spent {} ms", (System.currentTimeMillis() - t1));

        return outputFile;
    }


    public static EntitiesTransformStrategy createEntitiesTransformStrategy(Class<?> entityClass, EntityTransformStrategy strategy) {
        EntitiesTransformStrategy transformStrategy = new EntitiesTransformStrategy();
        transformStrategy.addModification(new TypedEntityMatch(entityClass, new AlwaysMatch()), strategy);
        return transformStrategy;
    }

    private static class IdSeparatorTransformer implements EntityTransformStrategy {

        protected static final String OTP_ID_SEPARATOR = "\\.";
        protected static final String EXTERNAL_ID_SEPARATOR = "\\:";

        @Override
        public void run(TransformContext context, GtfsMutableRelationalDao dao, Object entity) {
            if (entity instanceof ServiceCalendar) {
                transformAgencyAndId(((ServiceCalendar) entity).getServiceId());
            } else if (entity instanceof ServiceCalendarDate) {
                transformAgencyAndId(((ServiceCalendarDate) entity).getServiceId());
            } else if (entity instanceof Trip) {
                transformAgencyAndId(((Trip) entity).getServiceId());
            } else if (entity instanceof Stop) {
                Stop stop = (Stop) entity;
                stop.setParentStation(transform(stop.getParentStation()));
            }
            if (entity instanceof IdentityBean) {
                IdentityBean identityBean = (IdentityBean) entity;
                Serializable id = identityBean.getId();

                if (id instanceof AgencyAndId) {
                    transformAgencyAndId((AgencyAndId) id);
                } else if (id instanceof String) {
                    identityBean.setId(transform((String) id));
                }
            }
        }

        private void transformAgencyAndId(AgencyAndId agencyAndId) {
            agencyAndId.setId(transform(agencyAndId.getId()));
        }

        private String transform(String id) {
            if (id == null) {
                return null;
            }
            return id.replaceFirst(EXTERNAL_ID_SEPARATOR, OTP_ID_SEPARATOR).replaceFirst(EXTERNAL_ID_SEPARATOR, OTP_ID_SEPARATOR);
        }
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

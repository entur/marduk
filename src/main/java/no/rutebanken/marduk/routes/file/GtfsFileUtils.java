package no.rutebanken.marduk.routes.file;

import no.rutebanken.marduk.exceptions.MardukException;
import org.apache.commons.io.FileUtils;
import org.onebusaway.gtfs.serialization.GtfsEntitySchemaFactory;
import org.onebusaway.gtfs_merge.GtfsMerger;
import org.onebusaway.gtfs_merge.strategies.AbstractEntityMergeStrategy;
import org.onebusaway.gtfs_merge.strategies.EDuplicateDetectionStrategy;
import org.onebusaway.gtfs_merge.strategies.EntityMergeStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

public class GtfsFileUtils {
	private static Logger logger = LoggerFactory.getLogger(GtfsFileUtils.class);


	public static File mergeGtfsFilesInDirectory(String path) {
		return mergeGtfsFiles(FileUtils.listFiles(new File(path), new String[]{"zip"}, false));
	}

	static File mergeGtfsFiles(Collection<File> files) {

		try {
			logger.debug("Merging GTFS-files");
			long t1 = System.currentTimeMillis();
			File outputFile = File.createTempFile("marduk-merge", ".zip");
			buildGtfsMerger(EDuplicateDetectionStrategy.IDENTITY).run(new ArrayList<>(files), outputFile);
			logger.debug("Merged GTFS-files - spent {} ms", (System.currentTimeMillis() - t1));
			return outputFile;
		} catch (IOException ioException) {
			throw new MardukException("Merging of GTFS files failed", ioException);
		}

	}


	private static GtfsMerger buildGtfsMerger(EDuplicateDetectionStrategy duplicateDetectionStrategy) {
		GtfsMerger merger = new GtfsMerger();

		for (Class<?> entityClass : GtfsEntitySchemaFactory.getEntityClasses()) {
			EntityMergeStrategy entityMergeStrategy = merger.getEntityMergeStrategyForEntityType(entityClass);
			if (entityMergeStrategy instanceof AbstractEntityMergeStrategy) {
				((AbstractEntityMergeStrategy) entityMergeStrategy).setDuplicateDetectionStrategy(duplicateDetectionStrategy);
			}
		}
		return merger;
	}

}

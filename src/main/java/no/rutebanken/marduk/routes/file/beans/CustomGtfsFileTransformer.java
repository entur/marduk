package no.rutebanken.marduk.routes.file.beans;

import no.rutebanken.marduk.routes.file.ZipFileUtils;
import org.onebusaway.gtfs_transformer.GtfsTransformer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

import static no.rutebanken.marduk.routes.file.GtfsFileUtils.FEED_INFO_FILE_NAME;

public abstract class CustomGtfsFileTransformer {

    public File transform(File inputFile) {
        try {
            // Must replace feed_info.txt file with original because feed_id is being stripped away by transformation process
            ByteArrayOutputStream orgFeedInfo = new ZipFileUtils().extractFileFromZipFile(new FileInputStream(inputFile), FEED_INFO_FILE_NAME);
            return transform(inputFile, orgFeedInfo);
        } catch (IOException e) {
            throw new RuntimeException("Gtfs transformation failed with exception: " + e.getMessage(), e);
        }
    }


    public File transform(File inputFile, ByteArrayOutputStream feedInfo) {
        try {
            GtfsTransformer transformer = new GtfsTransformer();
            File outputFile = File.createTempFile("marduk-cleanup", ".zip");

            transformer.setGtfsInputDirectories(Arrays.asList(inputFile));
            transformer.setOutputDirectory(outputFile);

            addCustomTransformations(transformer);

            transformer.getReader().setOverwriteDuplicates(true);
            transformer.run();

            ZipFileUtils.replaceFileInZipFile(outputFile, FEED_INFO_FILE_NAME, feedInfo);
            return outputFile;
        } catch (Exception e) {
            throw new RuntimeException("Gtfs transformation failed with exception: " + e.getMessage(), e);
        }
    }

    protected abstract void addCustomTransformations(GtfsTransformer transformer);
}

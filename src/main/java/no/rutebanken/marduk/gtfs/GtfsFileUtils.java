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
import no.rutebanken.marduk.routes.file.MardukFileUtils;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.FileSystemUtils;
import org.zeroturnaround.zip.ByteSource;
import org.zeroturnaround.zip.ZipEntrySource;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class GtfsFileUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(GtfsFileUtils.class);

    public static final String FEED_INFO_FILE_NAME = "feed_info.txt";
    private static final byte[] FEED_INFO_FILE_CONTENT = "feed_id,feed_publisher_name,feed_publisher_url,feed_lang\nENTUR,Entur,https://www.entur.org,no".getBytes(StandardCharsets.UTF_8);


    private GtfsFileUtils() {
    }

    /**
     * Merge all GTFS files in a given directory.
     * Files are merged in alphabetical order.
     *
     * @param sourceDirectory the directory containing the GTFS archives.
     * @param gtfsExport      the type of GTFS export.
     * @return a delete-on-close input stream referring to the resulting merged GTFS archive.
     */
    public static InputStream mergeGtfsFilesInDirectory(File sourceDirectory, GtfsExport gtfsExport, boolean includeShapes) {

        if (sourceDirectory == null || !sourceDirectory.isDirectory()) {
            throw new MardukException(sourceDirectory + " is not a directory");
        }

        Collection<File> zipFiles = FileUtils.listFiles(sourceDirectory, new String[]{"zip"}, false);

        if (zipFiles.isEmpty()) {
            throw new MardukException(sourceDirectory + " does not contain any GTFS archive");
        }

        List<File> sortedZipFiles = zipFiles.stream()
                .sorted(Comparator.comparing(File::getName))
                .toList();

        try {
            return MardukFileUtils.createDeleteOnCloseInputStream(mergeGtfsFiles(sortedZipFiles, gtfsExport, includeShapes));
        } catch (IOException e) {
            throw new MardukException(e);
        }

    }

    /**
     * Merge a collection of GTFS files, add the feed_info.txt entry and return the merged GTFS file.
     *
     * @param zipFiles   GTFS archives to be merged.
     * @param gtfsExport the type of export.
     * @return a zip file containing the merged GTFS data.
     * @throws IOException
     */
    static File mergeGtfsFiles(Collection<File> zipFiles, GtfsExport gtfsExport, boolean includeShapes) throws IOException {

        long t1 = System.currentTimeMillis();
        LOGGER.debug("Merging GTFS files for export {}", gtfsExport);

        Path workingDirectory = Files.createTempDirectory("marduk-merge-gtfs");
        try {
            GtfsFileMerger gtfsFileMerger = new GtfsFileMerger(workingDirectory, gtfsExport, includeShapes);
            zipFiles.forEach(gtfsFileMerger::appendGtfs);
            Files.write(workingDirectory.resolve(FEED_INFO_FILE_NAME), FEED_INFO_FILE_CONTENT);
            File mergedFile = Files.createTempFile("marduk-merge-gtfs-merged", ".zip").toFile();
            ZipFileUtils.zipFilesInFolder(workingDirectory.toString(), mergedFile.toString());

            LOGGER.debug("Merged GTFS-files - spent {} ms", (System.currentTimeMillis() - t1));

            return mergedFile;
        } finally {
            FileSystemUtils.deleteRecursively(workingDirectory);
        }
    }


    public static void addOrReplaceFeedInfo(File gtfsZipFile) {
        ZipEntrySource feedInfoEntry = new ByteSource(FEED_INFO_FILE_NAME, FEED_INFO_FILE_CONTENT);
        ZipUtil.addOrReplaceEntries(gtfsZipFile, new ZipEntrySource[]{feedInfoEntry});
    }

    public static InputStream addOrReplaceFeedInfo(InputStream source) throws IOException {
        File tmpZip = File.createTempFile("marduk-add-or-replace-feed-info-", ".zip");
        Files.copy(source, tmpZip.toPath(), StandardCopyOption.REPLACE_EXISTING);
        addOrReplaceFeedInfo(tmpZip);
        return MardukFileUtils.createDeleteOnCloseInputStream(tmpZip);
    }
}

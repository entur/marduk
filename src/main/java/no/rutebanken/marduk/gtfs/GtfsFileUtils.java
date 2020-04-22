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
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GtfsFileUtils {

    private static Logger logger = LoggerFactory.getLogger(GtfsFileUtils.class);

    public static final String FEED_INFO_FILE_NAME = "feed_info.txt";
    private static final byte[] FEED_INFO_FILE_CONTENT = "feed_id,feed_publisher_name,feed_publisher_url,feed_lang\nENTUR,Entur,https://www.entur.org,no".getBytes(StandardCharsets.UTF_8);


    /**
     * Merge all GTFS files in a given directory.
     * Files are merged in alphabetical order.
     *
     * @param sourceDirectory the directory containing the GTFS archives.
     * @param gtfsExport      the type of GTFS export.
     * @return a delete-on-close input stream refering to the resulting merged GTFS archive.
     */
    public static InputStream mergeGtfsFilesInDirectory(String sourceDirectory, GtfsExport gtfsExport) {

        try (Stream<Path> walk = Files.walk(Paths.get(sourceDirectory))) {

            List<File> zipfilesInDirectory = walk.filter(Files::isRegularFile)
                    .filter(p -> p.endsWith(".zip"))
                    .sorted(Comparator.naturalOrder())
                    .map(Path::toFile)
                    .collect(Collectors.toList());

            return TempFileUtils.createDeleteOnCloseInputStream(mergeGtfsFiles(zipfilesInDirectory, gtfsExport));

        } catch (IOException e) {
            throw new MardukException(e);
        }
    }

    /**
     * Merge a collection of GTFS file entries, add the feed_info.txt entry and return the merged GTFS file.
     *
     * @param zipFiles   GTFS archives to be merged.
     * @param gtfsExport the type of export.
     * @return a zip file containing the merged GTFS data.
     * @throws IOException
     */
    static File mergeGtfsFiles(Collection<File> zipFiles, GtfsExport gtfsExport) throws IOException {

        long t1 = System.currentTimeMillis();
        logger.debug("Merging GTFS files for export {}", gtfsExport);

        Path workingDirectory = Files.createTempDirectory("marduk-merge-gtfs");
        try {
            GtfsFileMerger gtfsFileMerger = new GtfsFileMerger(workingDirectory, gtfsExport);
            zipFiles.forEach(zipFile -> gtfsFileMerger.appendGtfs(zipFile));
            Files.write(workingDirectory.resolve(FEED_INFO_FILE_NAME), FEED_INFO_FILE_CONTENT);
            File mergedFile = Files.createTempFile("marduk-merge-gtfs-merged", ".zip").toFile();
            ZipFileUtils.zipFilesInFolder(workingDirectory.toString(), mergedFile.toString());

            logger.debug("Merged GTFS-files - spent {} ms", (System.currentTimeMillis() - t1));

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
        return TempFileUtils.createDeleteOnCloseInputStream(tmpZip);
    }
}

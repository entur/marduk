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

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.zip.ZipUtil;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipFileUtils {
    private static Logger logger = LoggerFactory.getLogger(ZipFileUtils.class);

    public static Set<String> listFilesInZip(byte[] data) throws IOException {
        File tmpFile = TempFileUtils.createTempFile(data, "marduk-list-files-in-zip-", ".zip");
        Set<String> fileList = listFilesInZip(tmpFile);
        Files.delete(tmpFile.toPath());
        return fileList;
    }

        public static Set<String> listFilesInZip(File file) {
        try (ZipFile zipFile = new ZipFile(file, StandardCharsets.ISO_8859_1)) {
            return zipFile.stream().filter(ze -> !ze.isDirectory()).map(ze -> ze.getName()).collect(Collectors.toSet());
        } catch (IOException e) {
            return Collections.emptySet();
        }
    }

    public static InputStream rePackZipFile(byte[] data) throws IOException {

        logger.info("Repacking zipfile");
        File tmpSingleFolderzip = TempFileUtils.createTempFile(data, "marduk-single-folder-", ".zip");

        ZipFile zipFile = new ZipFile(tmpSingleFolderzip);

        File tmpFile = File.createTempFile("marduk-removed-single-folder-", ".zip");
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(tmpFile));

        String directoryName = "";

        ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(data));

        ZipEntry zipEntry = zipInputStream.getNextEntry();
        while (zipEntry != null) {
            if (!zipEntry.isDirectory()) {
                InputStream inputStream = zipFile.getInputStream(zipEntry);
                ZipEntry outEntry = new ZipEntry(zipEntry.getName().replace(directoryName, ""));
                out.putNextEntry(outEntry);
                byte[] buf = new byte[inputStream.available()];
                IOUtils.readFully(inputStream, buf);
                out.write(buf);
            } else {
                directoryName = zipEntry.getName();
            }
            zipEntry = zipInputStream.getNextEntry();
        }
        out.close();

        logger.info("File written to : {}", tmpFile.getAbsolutePath());
        zipFile.close();
        Files.delete(tmpSingleFolderzip.toPath());

        return TempFileUtils.createDeleteOnCloseInputStream(tmpFile);
    }


    public static File zipFilesInFolder(String folder, String targetFilePath) {
        File outputZip = new File(targetFilePath);
        ZipUtil.pack(new File(folder), outputZip);
        return outputZip;
    }

    public static byte[] extractFileFromZipFile(File file, String extractFileName) {
        return ZipUtil.unpackEntry(file, extractFileName);
    }

    public static boolean zipFileContainsSingleFolder(byte[] data) throws IOException {
        File tmpFile = TempFileUtils.createTempFile(data, "marduk-zip-file-contains-single-folder-", ".zip");
        boolean singleFolder = zipFileContainsSingleFolder(tmpFile);
        Files.delete(tmpFile.toPath());
        return singleFolder;
    }

    public static boolean zipFileContainsSingleFolder(File inputFile) throws IOException {

        ZipFile zipFile = new ZipFile(inputFile);
            boolean allFilesInSingleDirectory = false;
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            String directoryName = "";
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                if (zipEntry.isDirectory()) {
                    allFilesInSingleDirectory = true;
                    directoryName = zipEntry.getName();
                } else {
                    if (!zipEntry.getName().startsWith(directoryName)) {
                        allFilesInSingleDirectory = false;
                        break;
                    }
                }
            }

        return allFilesInSingleDirectory;
    }

    public static void unzipFile(InputStream inputStream, String targetFolder) {
        ZipUtil.unpack(inputStream, new File(targetFolder));
    }






}
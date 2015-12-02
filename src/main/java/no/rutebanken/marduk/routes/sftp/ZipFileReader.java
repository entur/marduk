package no.rutebanken.marduk.routes.sftp;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

public class ZipFileReader {

    public Set<String> listFilesInZip(File file) {
        try (ZipFile zipFile = new ZipFile(file)) {
            return zipFile.stream()
                    .filter(ze -> !ze.isDirectory())
                    .map(ze -> ze.getName())
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            return Collections.emptySet();
        }
    }

    public Set<String> listFilesInZip(byte[] bytes) {
        Set<String> fileNames = new HashSet<>();
        try (ZipArchiveInputStream zipInputStream = new ZipArchiveInputStream(new ByteArrayInputStream(bytes))){
            ZipArchiveEntry zipArchiveEntry = zipInputStream.getNextZipEntry();
            while (zipArchiveEntry != null) {
                fileNames.add(zipArchiveEntry.getName());
                zipArchiveEntry = zipInputStream.getNextZipEntry();
            }
            return fileNames;
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptySet();
        }
    }

}
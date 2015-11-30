package no.rutebanken.marduk.routes.sftp;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

public class ZipFileReader {

    public ZipFileReader() {
    }

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
}
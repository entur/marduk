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
 */

package no.rutebanken.marduk.repository;

import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.routes.chouette.json.exporter.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Simple file-based blob store repository for testing purpose.
 */
@Component
@Profile("local-disk-blobstore")
@Scope("prototype")
public class LocalDiskBlobStoreRepository implements BlobStoreRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalDiskBlobStoreRepository.class);

    @Value("${blobstore.local.folder:files/blob}")
    private String baseFolder;

    private String containerName;

    @Override
    public BlobStoreFiles listBlobs(String prefix) {
        return listBlobs(Collections.singletonList(prefix));
    }

    @Override
    public BlobStoreFiles listBlobs(Collection<String> prefixes) {

        BlobStoreFiles blobStoreFiles = new BlobStoreFiles();
        for (String prefix : prefixes) {
            if (Paths.get(getContainerFolder(), prefix).toFile().isDirectory()) {
                try (Stream<Path> walk = Files.walk(Paths.get(getContainerFolder(), prefix))) {
                    List<BlobStoreFiles.File> result = walk.filter(Files::isRegularFile)
                            .map(path -> new BlobStoreFiles.File(Paths.get(getContainerFolder()).relativize(path).toString(), getFileCreationDate(path), getFileLastModifiedDate(path), getFileSize(path))).collect(Collectors.toList());
                    blobStoreFiles.add(result);
                } catch (IOException e) {
                    throw new MardukException(e);
                }
            }

        }
        return blobStoreFiles;
    }

    private String getContainerFolder() {
        return baseFolder + File.separator + containerName;
    }


    @Override
    public BlobStoreFiles listBlobsFlat(String prefix) {
        List<BlobStoreFiles.File> files = listBlobs(prefix).getFiles();
        List<BlobStoreFiles.File> result = files.stream().map(file -> new BlobStoreFiles.File(file.getName().replaceFirst(prefix, ""), file.getCreated(), file.getUpdated(), file.getFileSize())).collect(Collectors.toList());
        BlobStoreFiles blobStoreFiles = new BlobStoreFiles();
        blobStoreFiles.add(result);
        return blobStoreFiles;
    }

    @Override
    public InputStream getBlob(String objectName) {
        LOGGER.debug("get blob called in local-disk blob store on {}", objectName);
        Path path = Paths.get(getContainerFolder()).resolve(objectName);
        if (!path.toFile().exists()) {
            LOGGER.debug("getBlob(): File not found in local-disk blob store: {} ", path);
            return null;
        }
        LOGGER.debug("getBlob(): File found in local-disk blob store: {} ", path);
        try {
            // converted as ByteArrayInputStream so that Camel stream cache can reopen it
            // since ByteArrayInputStream.close() does nothing
            return new ByteArrayInputStream(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new MardukException(e);
        }
    }

    @Override
    public void uploadBlob(String objectName, InputStream inputStream, boolean makePublic) {
        LOGGER.debug("Upload blob called in local-disk blob store on {}", objectName);
        try {
            Path localPath = Paths.get(objectName);
            Path parentDirectory = localPath.getParent();
            Path folder = parentDirectory == null ? Paths.get(getContainerFolder()) : Paths.get(getContainerFolder()).resolve(parentDirectory);
            Files.createDirectories(folder);

            Path fullPath = Paths.get(getContainerFolder()).resolve(localPath);
            Files.deleteIfExists(fullPath);

            Files.copy(inputStream, fullPath);
        } catch (IOException e) {
            throw new MardukException(e);
        }
    }

    @Override
    public void copyBlob(String sourceContainerName, String sourceObjectName, String targetContainerName, String targetObjectName, boolean makePublic) {
        try {
            Files.copy(Path.of(baseFolder, sourceContainerName, sourceObjectName), Path.of(baseFolder, targetContainerName, targetObjectName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new MardukException(e);
        }
    }

    @Override
    public void copyAllBlobs(String sourceContainerName, String prefix, String targetContainerName, String targetPrefix, boolean makePublic) {
        // no-op implementation for local disk blobstore
    }

    @Override
    public void uploadBlob(String objectName, InputStream inputStream, boolean makePublic, String contentType) {
        uploadBlob(objectName, inputStream, makePublic);
    }

    @Override
    public void setContainerName(String containerName) {
       this.containerName = containerName;
    }

    @Override
    public boolean delete(String objectName) {
        LOGGER.debug("Delete blob called in local-disk blob store on: {}", objectName);
        Path path = Paths.get(getContainerFolder()).resolve(objectName);
        if (!path.toFile().exists()) {
            LOGGER.debug("delete(): File not found in local-disk blob store: {} ", path);
            return false;
        }
        try {
            Files.delete(path);
            return true;
        } catch (IOException e) {
            throw new MardukException(e);
        }
    }

    @Override
    public boolean deleteAllFilesInFolder(String folder) {
        Path folderToDelete = Paths.get(getContainerFolder()).resolve(folder);
        if (folderToDelete.toFile().isDirectory()) {
            try (Stream<Path> paths = Files.walk(folderToDelete)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                throw new MardukException(e);
                            }
                        });
                return true;
            } catch (IOException e) {
                throw new MardukException(e);
            }
        }
        return false;
    }

    private static LocalDateTime getFileCreationDate(Path path) {
        try {
            BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
            return DateUtils.fromEpoch(attr.creationTime().toMillis());
        } catch (IOException e) {
            throw new MardukException(e);
        }
    }

    private static LocalDateTime getFileLastModifiedDate(Path path) {
        return DateUtils.fromEpoch(path.toFile().lastModified());
    }

    private static long getFileSize(Path path) {
        return path.toFile().length();
    }
}

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

import com.google.cloud.storage.Storage;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.exceptions.MardukException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Simple file-based blob store repository for testing purpose.
 */
@Component
@Profile("local-disk-blobstore")
public class LocalDiskBlobStoreRepository implements BlobStoreRepository {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${blobstore.local.folder:files/blob}")
    private String baseFolder;

    @Override
    public BlobStoreFiles listBlobs(String prefix) {
        return listBlobs(Collections.singletonList(prefix));
    }

    @Override
    public BlobStoreFiles listBlobs(Collection<String> prefixes) {

        BlobStoreFiles blobStoreFiles = new BlobStoreFiles();
        for (String prefix : prefixes) {
            if (Paths.get(baseFolder, prefix).toFile().isDirectory()) {
                try (Stream<Path> walk = Files.walk(Paths.get(baseFolder, prefix))) {
                    List<BlobStoreFiles.File> result = walk.filter(Files::isRegularFile)
                            .map(path -> new BlobStoreFiles.File(Paths.get(baseFolder).relativize(path).toString(), getFileCreationDate(path), getFileLastModifiedDate(path), getFileSize(path))).collect(Collectors.toList());
                    blobStoreFiles.add(result);
                } catch (IOException e) {
                    throw new MardukException(e);
                }
            }

        }
        return blobStoreFiles;
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
        logger.debug("get blob called in local-disk blob store on {}", objectName);
        Path path = Paths.get(baseFolder).resolve(objectName);
        if (!path.toFile().exists()) {
            logger.debug("getBlob(): File not found in local-disk blob store: {} ", path);
            return null;
        }
        logger.debug("getBlob(): File found in local-disk blob store: {} ", path);
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
        logger.debug("Upload blob called in local-disk blob store on {}", objectName);
        try {
            Path localPath = Paths.get(objectName);
            Path parentDirectory = localPath.getParent();
            Path folder = parentDirectory == null ? Paths.get(baseFolder) : Paths.get(baseFolder).resolve(parentDirectory);
            Files.createDirectories(folder);

            Path fullPath = Paths.get(baseFolder).resolve(localPath);
            Files.deleteIfExists(fullPath);

            Files.copy(inputStream, fullPath);
        } catch (IOException e) {
            throw new MardukException(e);
        }
    }

    @Override
    public void copyBlob(String sourceObjectName, String targetObjectName, boolean makePublic) {
        Path sourceLocalPath = Paths.get(sourceObjectName);
        Path sourceFullPath = Paths.get(baseFolder).resolve(sourceLocalPath);
        Path targetLocalPath = Paths.get(targetObjectName);
        Path targetFullPath = Paths.get(baseFolder).resolve(targetLocalPath);
        try {

            // create target parent directories if missing
            Path parentDirectory = targetLocalPath.getParent();
            Path folder = parentDirectory == null ? Paths.get(baseFolder) : Paths.get(baseFolder).resolve(parentDirectory);
            Files.createDirectories(folder);

            Files.copy(sourceFullPath, targetFullPath);
        } catch (IOException e) {
            throw new MardukException(e);
        }

    }

    @Override
    public void copyAllBlobs(String sourceContainerName, String prefix, String targetContainerName, String targetPrefix, boolean makePublic) {

    }

    @Override
    public void uploadBlob(String objectName, InputStream inputStream, boolean makePublic, String contentType) {
        uploadBlob(objectName, inputStream, makePublic);
    }

    @Override
    public void setStorage(Storage storage) {
        // not applicable to local disk blobstore
    }

    @Override
    public void setContainerName(String containerName) {
        // not applicable to local disk blobstore
    }

    @Override
    public boolean delete(String objectName) {
        logger.debug("Delete blob called in local-disk blob store on: {}", objectName);
        Path path = Paths.get(baseFolder).resolve(objectName);
        if (!path.toFile().exists()) {
            logger.debug("delete(): File not found in local-disk blob store: {} ", path);
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
        try (Stream<Path> paths = Files.walk(Paths.get(baseFolder).resolve(folder))) {
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

    private static Date getFileCreationDate(Path path) {
        try {
            BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
            return new Date(attr.creationTime().toMillis());
        } catch (IOException e) {
            throw new MardukException(e);
        }
    }

    private static Date getFileLastModifiedDate(Path path) {
        return new Date(path.toFile().lastModified());
    }

    private static long getFileSize(Path path) {
        return path.toFile().length();
    }
}

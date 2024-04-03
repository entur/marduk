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
import org.rutebanken.helper.gcp.repository.LocalDiskBlobStoreRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Simple file-based blob store repository for testing purpose.
 */

public class LocalDiskMardukBlobStoreRepository extends LocalDiskBlobStoreRepository implements MardukBlobStoreRepository {

    public LocalDiskMardukBlobStoreRepository(String baseFolder) {
        super(baseFolder);
    }

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
                            .map(path -> new BlobStoreFiles.File(Paths.get(getContainerFolder()).relativize(path).toString(), getFileCreationDate(path), getFileLastModifiedDate(path), getFileSize(path))).toList();
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
        List<BlobStoreFiles.File> result = files.stream().map(file -> new BlobStoreFiles.File(file.getName().replaceFirst(prefix, ""), file.getCreated(), file.getUpdated(), file.getFileSize())).toList();
        BlobStoreFiles blobStoreFiles = new BlobStoreFiles();
        blobStoreFiles.add(result);
        return blobStoreFiles;
    }


    private static Instant getFileCreationDate(Path path) {
        try {
            BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
            return Instant.ofEpochMilli(attr.creationTime().toMillis());
        } catch (IOException e) {
            throw new MardukException(e);
        }
    }

    private static Instant getFileLastModifiedDate(Path path) {
        return Instant.ofEpochMilli(path.toFile().lastModified());
    }

    private static long getFileSize(Path path) {
        return path.toFile().length();
    }
}

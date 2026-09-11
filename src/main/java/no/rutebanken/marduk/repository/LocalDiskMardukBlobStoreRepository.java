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
import org.rutebanken.helper.storage.repository.LocalDiskBlobStoreRepository;

import java.io.File;
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

    /**
     * List the blobs whose name starts with one of the prefixes, like a Google Cloud Storage prefix query: the
     * prefix may be a folder ({@code outbound/netex/}), the complete name of a blob
     * ({@code outbound/netex/rb_vyg-aggregated-netex.zip}) or the beginning of a blob name
     * ({@code outbound/netex/rb_vyg-}).
     * <p>
     * A prefix that is the beginning of a folder name ({@code outbound/net}) matches nothing, unlike in Google Cloud
     * Storage where folders are only a naming convention. No caller relies on that form.
     */
    @Override
    public BlobStoreFiles listBlobs(Collection<String> prefixes) {

        BlobStoreFiles blobStoreFiles = new BlobStoreFiles();
        for (String prefix : prefixes) {
            Path searchRoot = searchRoot(prefix);
            if (searchRoot == null) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(searchRoot)) {
                List<BlobStoreFiles.File> result = walk.filter(Files::isRegularFile)
                        .filter(path -> blobName(path).startsWith(prefix))
                        .map(path -> new BlobStoreFiles.File(blobName(path), getFileCreationDate(path), getFileLastModifiedDate(path), getFileSize(path))).toList();
                blobStoreFiles.add(result);
            } catch (IOException e) {
                throw new MardukException(e);
            }
        }
        return blobStoreFiles;
    }

    /**
     * The folder to walk to find the blobs matching a prefix: the prefix itself when it is a folder, otherwise the
     * folder holding it, since the prefix then names a blob or the beginning of a blob name.
     *
     * @return null if that folder does not exist, in which case no blob can match the prefix.
     */
    private Path searchRoot(String prefix) {
        Path path = Paths.get(getContainerFolder(), prefix);
        if (Files.isDirectory(path)) {
            return path;
        }
        Path parent = path.getParent();
        return parent != null && Files.isDirectory(parent) ? parent : null;
    }

    /**
     * The name of a blob: its path relative to the container folder, with '/' as separator whatever the platform.
     */
    private String blobName(Path path) {
        return Paths.get(getContainerFolder()).relativize(path).toString().replace(File.separatorChar, '/');
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

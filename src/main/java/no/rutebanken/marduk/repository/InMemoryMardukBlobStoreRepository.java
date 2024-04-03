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

package no.rutebanken.marduk.repository;

import no.rutebanken.marduk.domain.BlobStoreFiles;
import org.rutebanken.helper.storage.repository.InMemoryBlobStoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Simple memory-based blob store repository for testing purpose.
 */
public class InMemoryMardukBlobStoreRepository extends InMemoryBlobStoreRepository implements MardukBlobStoreRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryMardukBlobStoreRepository.class);

    public InMemoryMardukBlobStoreRepository(Map<String, Map<String, byte[]>> blobsInContainers) {
        super(blobsInContainers);
    }

    @Override
    public BlobStoreFiles listBlobs(String prefix) {
        return listBlobs(Collections.singletonList(prefix));
    }

    @Override
    public BlobStoreFiles listBlobs(Collection<String> prefixes) {
        LOGGER.debug("list blobs called in in-memory blob store");
        List<BlobStoreFiles.File> files = getBlobsForCurrentContainer().keySet().stream()
                .filter(fileName -> prefixes.stream().anyMatch(fileName::startsWith))
                .map(fileName -> new BlobStoreFiles.File(fileName, Instant.now(), Instant.now(), (long) getBlobsForCurrentContainer().get(fileName).length))
                .toList();
        BlobStoreFiles blobStoreFiles = new BlobStoreFiles();
        blobStoreFiles.add(files);
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


}

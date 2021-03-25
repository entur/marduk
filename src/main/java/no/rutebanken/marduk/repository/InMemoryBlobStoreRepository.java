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
import no.rutebanken.marduk.exceptions.MardukException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Repository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Simple memory-based blob store repository for testing purpose.
 */
@Repository
@Profile("in-memory-blobstore")
@Scope("prototype")
public class InMemoryBlobStoreRepository implements BlobStoreRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryBlobStoreRepository.class);


    /**
     * Autowire a shared map so that each prototype bean can access blobs from other containers.
     * This is needed for {@link #copyBlob(String, String, String, String, boolean)}
     */
    @Autowired
    private Map<String, Map<String, byte[]>> blobsInContainers;

    private String containerName;

    private Map<String, byte[]> getBlobsForCurrentContainer() {
        return getBlobsForContainer(containerName);
    }

    private Map<String, byte[]> getBlobsForContainer(String aContainer) {
        return blobsInContainers.computeIfAbsent(aContainer, k -> Collections.synchronizedMap(new HashMap<>()));
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
                .map(fileName -> new BlobStoreFiles.File(fileName, LocalDateTime.now(), LocalDateTime.now(), (long) getBlobsForCurrentContainer().get(fileName).length))
                .collect(Collectors.toList());
        BlobStoreFiles blobStoreFiles = new BlobStoreFiles();
        blobStoreFiles.add(files);
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
        LOGGER.debug("get blob called in in-memory blob store");
        byte[] data = getBlobsForCurrentContainer().get(objectName);
        return (data == null) ? null : new ByteArrayInputStream(data);
    }

    @Override
    public void uploadBlob(String objectName, InputStream inputStream, boolean makePublic, String contentType) {
        uploadBlob(objectName, inputStream, makePublic);
    }

    @Override
    public void uploadBlob(String objectName, InputStream inputStream, boolean makePublic) {
        try {
            LOGGER.debug("upload blob called in in-memory blob store");
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            IOUtils.copy(inputStream, byteArrayOutputStream);
            byte[] data = byteArrayOutputStream.toByteArray();
            getBlobsForCurrentContainer().put(objectName, data);
        } catch (IOException e) {
            throw new MardukException(e);
        }
    }

    @Override
    public void copyBlob(String sourceContainerName, String sourceObjectName, String targetContainerName, String targetObjectName, boolean makePublic) {
        byte[] sourceData = getBlobsForContainer(sourceContainerName).get(sourceObjectName);
        getBlobsForContainer(targetContainerName).put(targetObjectName, sourceData);
    }

    @Override
    public void copyAllBlobs(String sourceContainerName, String prefix, String targetContainerName, String targetPrefix, boolean makePublic) {
        // no-op implementation for in-memory blobstore
    }

    @Override
    public boolean delete(String objectName) {
        getBlobsForCurrentContainer().remove(objectName);
        return true;
    }

    @Override
    public boolean deleteAllFilesInFolder(String folder) {
        listBlobs(folder).getFiles().forEach(file -> delete(file.getName()));
        return true;
    }

    @Override
    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

}

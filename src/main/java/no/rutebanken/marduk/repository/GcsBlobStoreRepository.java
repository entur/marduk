package no.rutebanken.marduk.repository;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import org.apache.commons.lang3.StringUtils;
import org.rutebanken.helper.gcp.BlobStoreHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.util.Date;
import java.util.Iterator;

@Repository
@Profile("gcs-blobstore")
@Scope("prototype")
public class GcsBlobStoreRepository implements BlobStoreRepository {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private Storage storage;

    private String containerName;

    @Override
    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    @Override
    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    @Override
    public BlobStoreFiles listBlobs(String prefix) {
        Iterator<Blob> blobIterator = BlobStoreHelper.listAllBlobsRecursively(storage, containerName, prefix);
        BlobStoreFiles blobStoreFiles = new BlobStoreFiles();
        while (blobIterator.hasNext()) {
            Blob blob = blobIterator.next();
            blobStoreFiles.add(new BlobStoreFiles.File(blob.getName(), new Date(blob.getUpdateTime()), blob.getSize()));
        }
        return blobStoreFiles;
    }

    @Override
    public BlobStoreFiles listBlobsFlat(String prefix) {
        Iterator<Blob> blobIterator = BlobStoreHelper.listAllBlobsRecursively(storage, containerName, prefix);
        BlobStoreFiles blobStoreFiles = new BlobStoreFiles();
        while (blobIterator.hasNext()) {
            Blob blob = blobIterator.next();
            String fileName = blob.getName().replace(prefix, "");
            if (!StringUtils.isEmpty(fileName)) {
                blobStoreFiles.add(new BlobStoreFiles.File(fileName, new Date(blob.getUpdateTime()), blob.getSize()));
            }
        }
        return blobStoreFiles;
    }

    @Override
    public InputStream getBlob(String name) {
        return BlobStoreHelper.getBlob(storage, containerName, name);
    }

    @Override
    public void uploadBlob(String name, InputStream inputStream, boolean makePublic) {
        BlobStoreHelper.uploadBlob(storage, containerName, name, inputStream, makePublic);
    }

    @Override
    public void uploadBlob(String name, InputStream inputStream, boolean makePublic, String contentType) {
        BlobStoreHelper.uploadBlob(storage, containerName, name, inputStream, makePublic, contentType);
    }

    @Override
    public boolean delete(String objectName) {
        return BlobStoreHelper.delete(storage, BlobId.of(containerName, objectName));
    }

    @Override
    public boolean deleteAllFilesInFolder(String folder) {
        return BlobStoreHelper.deleteBlobsByPrefix(storage, containerName, folder);
    }
}

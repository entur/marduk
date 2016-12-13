package no.rutebanken.marduk.repository;

import com.google.cloud.storage.Storage;
import no.rutebanken.marduk.domain.BlobStoreFiles;

import java.io.InputStream;

public interface BlobStoreRepository {

    BlobStoreFiles listBlobs(String prefix);

    InputStream getBlob(String objectName);

    void uploadBlob(String objectName, InputStream inputStream, boolean makePublic);

    void setStorage(Storage storage);

    void setContainerName(String containerName);

    boolean delete(String objectName);

}

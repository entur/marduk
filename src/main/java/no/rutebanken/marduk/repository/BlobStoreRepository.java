package no.rutebanken.marduk.repository;

import com.google.cloud.storage.Storage;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import org.springframework.util.CollectionUtils;

import java.io.InputStream;
import java.util.Collection;

public interface BlobStoreRepository {

    BlobStoreFiles listBlobs(Collection<String> prefixes);

    BlobStoreFiles listBlobs(String prefix);

    BlobStoreFiles listBlobsFlat(String prefix);

    InputStream getBlob(String objectName);

    void uploadBlob(String objectName, InputStream inputStream, boolean makePublic);

    void uploadBlob(String objectName, InputStream inputStream, boolean makePublic, String contentType);

    void setStorage(Storage storage);

    void setContainerName(String containerName);

    boolean delete(String objectName);

    boolean deleteAllFilesInFolder(String folder);

}

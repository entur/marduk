package no.rutebanken.marduk.repository;

import no.rutebanken.marduk.domain.BlobStoreFiles;

import java.io.InputStream;

public interface BlobStoreRepository {

    BlobStoreFiles listBlobs(String prefix);

    InputStream getBlob(String objectName);

    void uploadBlob(String objectName, InputStream inputStream, boolean makePublic);
}

package no.rutebanken.marduk.services;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.repository.BlobStoreRepository;
import org.apache.camel.Header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class BlobStoreService {

    @Autowired
    BlobStoreRepository repository;

    public BlobStoreFiles listBlobs(@Header(value = Constants.CHOUETTE_REFERENTIAL) String referential) {
       return repository.listBlobs(Constants.BLOBSTORE_PATH_INBOUND_RECEIVED + referential + "/");
    }

    public InputStream getBlob(@Header(value = Constants.FILE_HANDLE) String name) {
        return repository.getBlob(name);
    }

    public void uploadBlob(@Header(value = Constants.FILE_HANDLE) String name, InputStream inputStream) {
        repository.uploadBlob(name, inputStream);
    }
}

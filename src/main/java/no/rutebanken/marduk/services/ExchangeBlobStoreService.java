package no.rutebanken.marduk.services;

import com.google.cloud.storage.Storage;
import no.rutebanken.marduk.repository.BlobStoreRepository;
import org.apache.camel.Header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.InputStream;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;

@Service
public class ExchangeBlobStoreService {

    @Autowired
    private BlobStoreRepository repository;

    @Autowired
    Storage exchangeStorage;

    @Value("${blobstore.gcs.exchange.container.name}")
    String containerName;

    @PostConstruct
    public void init(){
        repository.setStorage(exchangeStorage);
        repository.setContainerName(containerName);
    }

    public InputStream getBlob(@Header(value = FILE_HANDLE) String name) {
        return repository.getBlob(name);
    }

}

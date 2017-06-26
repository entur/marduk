package no.rutebanken.marduk.services;

import com.google.cloud.storage.Storage;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.repository.BlobStoreRepository;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.InputStream;

@Service
public class OtpReportBlobStoreService {

    @Autowired
    private BlobStoreRepository repository;

    @Autowired
    private Storage exchangeStorage;

    @Value("${blobstore.gcs.otpreport.container.name}")
    private String containerName;

    @PostConstruct
    public void init() {
        repository.setStorage(exchangeStorage);
        repository.setContainerName(containerName);
    }

    public void uploadBlob(String name, InputStream inputStream, boolean makePublic) {
        if (name.endsWith(".html")) {
            repository.uploadBlob(name, inputStream, makePublic, "text/html");
        } else {
            repository.uploadBlob(name, inputStream, makePublic);
        }
    }

}

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

import static no.rutebanken.marduk.Constants.FILE_HANDLE;

@Service
public class ExchangeBlobStoreService {

    @Autowired
    private BlobStoreRepository repository;

    @Autowired
    private Storage exchangeStorage;

    @Value("${blobstore.gcs.exchange.container.name}")
    private String containerName;

    @PostConstruct
    public void init(){
        repository.setStorage(exchangeStorage);
        repository.setContainerName(containerName);
    }

    public void uploadBlob(@Header(value = Constants.FILE_HANDLE) String name, InputStream inputStream, Exchange exchange) {
        ExchangeUtils.addHeadersAndAttachments(exchange);
        repository.uploadBlob(name, inputStream, false);
    }

    public InputStream getBlob(@Header(value = FILE_HANDLE) String name, Exchange exchange) {
        ExchangeUtils.addHeadersAndAttachments(exchange);
        return repository.getBlob(name);
    }

    public boolean deleteBlob(@Header(value = FILE_HANDLE) String name, Exchange exchange) {
        ExchangeUtils.addHeadersAndAttachments(exchange);
        return repository.delete(name);
    }

}

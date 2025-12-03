package no.rutebanken.marduk.services;

import no.rutebanken.marduk.repository.MardukBlobStoreRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AshurExchangeBlobStoreService extends AbstractBlobStoreService {
    public AshurExchangeBlobStoreService(@Value("${blobstore.gcs.internal.container.name}") String containerName, MardukBlobStoreRepository repository) {
        super(containerName, repository);
    }
}

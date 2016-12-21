package no.rutebanken.marduk.config;

import com.google.cloud.storage.Storage;
import org.rutebanken.helper.gcp.BlobStoreHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("gcs-blobstore")
public class GcsStorageConfig {

    @Value("${blobstore.gcs.credential.path}")
    private String credentialPath;

    @Value("${blobstore.gcs.exchange.credential.path}")
    private String exchangeCredentialPath;

    @Value("${blobstore.gcs.project.id}")
    private String projectId;

    @Bean
    public Storage storage() {
        return BlobStoreHelper.getStorage(credentialPath, projectId);
    }

    @Bean
    public Storage exchangeStorage() {
        return BlobStoreHelper.getStorage(exchangeCredentialPath, projectId);
    }

}

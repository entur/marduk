package no.rutebanken.marduk.config;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("dev")
public class InMemoryGcsStorageConfig {

    @Bean
    public Storage storage() {
        return getStorage();
    }

    @Bean
    public Storage exchangeStorage() {
        return getStorage();
    }

    private Storage getStorage() {
        StorageOptions options = StorageOptions.builder().projectId("1234").build();
        return options.service();
    }


}

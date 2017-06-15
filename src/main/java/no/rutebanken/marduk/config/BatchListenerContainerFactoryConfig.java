package no.rutebanken.marduk.config;

import org.rutebanken.helper.jms.batch.BatchListenerContainerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BatchListenerContainerFactoryConfig {

    @Bean
    public BatchListenerContainerFactory batchListenerContainerFactory(@Value("${rutebanken.jms.batch.size:100}") int batchSize) {
        return new BatchListenerContainerFactory(batchSize);

    }
}

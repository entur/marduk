
package no.rutebanken.marduk.security;

import no.rutebanken.marduk.repository.ProviderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class NetexBlocksAuthorizationService {

    @Autowired
    private ProviderRepository providerRepository;

    @Value("${netex.export.block.consumers}")
    protected List<String> netexBlockConsumers;

    @Value("#{${netex.export.block.authorization}}")
    protected Map<String, String> authorizedProviderForConsumer;


    /**
     * A valid consumer is either a provider registered in the provider repository or a registered consumer
     *
     * @param consumerCodeSpace
     * @return
     */
    public boolean isValidConsumer(String consumerCodeSpace) {
        return providerRepository.getProviderId(consumerCodeSpace) != null || netexBlockConsumers.contains(consumerCodeSpace);
    }

    public void authorizeConsumerForProvider(String consumer, String provider) {
        // providers can access their own dataset
        if (consumer.equals(provider)) {
            return;
        }
        if (authorizedProviderForConsumer.get(consumer) == null || !authorizedProviderForConsumer.get(consumer).contains(provider)) {
            throw new AccessDeniedException("Insufficient privileges for operation");
        }

    }

}

package no.rutebanken.marduk.repository;

import no.rutebanken.marduk.domain.Provider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.List;

@Repository
public class RestProviderRepository implements ProviderRepository {

    @Value("${rest.provider.service.url}")
    private String providerServiceBaseUrl;

    @Override
    public Collection<Provider> getProviders() {
        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<List<Provider>> rateResponse =
                restTemplate.exchange(providerServiceBaseUrl + "/all",
                        HttpMethod.GET, null, new ParameterizedTypeReference<List<Provider>>() {
                        });
        return rateResponse.getBody();
    }

    @Override
    public Provider getProvider(Long id) {
        RestTemplate restTemplate = new RestTemplate();
        return restTemplate.getForObject(providerServiceBaseUrl + "/" + id, Provider.class);
    }

}

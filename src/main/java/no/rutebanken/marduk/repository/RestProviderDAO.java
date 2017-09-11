package no.rutebanken.marduk.repository;

import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.security.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@Component
public class RestProviderDAO {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${providers.api.url}")
    private String restServiceUrl;


    @Autowired
    private TokenService tokenService;


    public Collection<Provider> getProviders() {
        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<List<Provider>> rateResponse =
                restTemplate.exchange(restServiceUrl,
                        HttpMethod.GET, getEntityWithAuthenticationToken(), new ParameterizedTypeReference<List<Provider>>() {
                        });
        return rateResponse.getBody();
    }

    private HttpEntity<String> getEntityWithAuthenticationToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + tokenService.getToken());
        return new HttpEntity<>(headers);
    }

}

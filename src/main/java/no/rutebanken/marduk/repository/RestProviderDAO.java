/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.repository;

import no.rutebanken.marduk.domain.Provider;

import org.entur.jwt.client.AccessToken;
import org.entur.jwt.client.AccessTokenException;
import org.entur.jwt.client.AccessTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.List;


@Component
public class RestProviderDAO {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${providers.api.url}")
    private String restServiceUrl;

    @Autowired
    private RestTemplate restTemplate;

	@Autowired
	private AccessTokenProvider accessTokenProvider;

    public Collection<Provider> getProviders() throws AccessTokenException {
        try {
        	return getProvidersImpl(false);
        } catch(HttpClientErrorException e) {
        	if(e.getStatusCode().equals(HttpStatus.UNAUTHORIZED)) {
        		// refresh token and retry once
            	return getProvidersImpl(true);
        	}
            throw e;
        }
    }

	private Collection<Provider> getProvidersImpl(boolean refresh) throws RestClientException, AccessTokenException {
        HttpHeaders headers = new HttpHeaders();
        AccessToken accessToken = accessTokenProvider.getAccessToken(refresh);
        headers.set("Authorization", "Bearer " + accessToken.getValue());
		
		ResponseEntity<List<Provider>> rateResponse =
                restTemplate.exchange(restServiceUrl,
                        HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<List<Provider>>() {
                        });
        return rateResponse.getBody();
	}

}

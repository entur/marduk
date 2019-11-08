package no.rutebanken.marduk.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.util.Arrays;

import no.rutebanken.marduk.repository.CacheProviderRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.annotation.DirtiesContext.MethodMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import no.rutebanken.marduk.test.TestApp;

@ActiveProfiles({"default", "in-memory-blobstore", "google-pubsub-emulator"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestApp.class)
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
public class AdminRestTest {

	@MockBean
	public CacheProviderRepository providerRepository;

    @Value("${server.admin.port}")
    public String adminPort;

    @Disabled
	@Test
	// TODO some other test has loaded the context without the REST component (or shut it down)
	public void testThatSwaggerSpecificationIsAvailable() throws Exception {
		URI uri = new URI("http://localhost:" + adminPort + "/services/swagger.json");

		RestTemplate restTemplate = new RestTemplate();
		
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
		HttpEntity <String> entity = new HttpEntity<String>(headers);

		ResponseEntity<String> exchange = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
		
		assertEquals(200, exchange.getStatusCodeValue());
	}

}

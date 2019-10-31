package no.rutebanken.marduk;

import org.entur.jwt.client.AccessToken;
import org.entur.jwt.client.AccessTokenException;
import org.entur.jwt.client.AccessTokenProvider;
import org.entur.jwt.junit5.entur.test.PartnerAccessToken;
import org.entur.jwt.junit5.entur.test.PartnerAuthorizationServer;
import org.entur.jwt.jwk.SigningKeyUnavailableException;
import org.entur.jwt.spring.filter.JwtAuthenticationFilter;
import org.entur.jwt.verifier.JwtVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import static org.mockito.Mockito.*;
import com.auth0.jwt.interfaces.DecodedJWT;

import no.rutebanken.marduk.repository.RestProviderDAO;
import no.rutebanken.marduk.test.TestApp;

@PartnerAuthorizationServer
@ActiveProfiles({"default", "in-memory-blobstore", "google-pubsub-emulator"})
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, classes = TestApp.class)
public class ContextTest {

	@Autowired
	private JwtAuthenticationFilter<?> filter;
	
	@MockBean
	private AccessTokenProvider provider;
	
	@Autowired
	JwtVerifier<DecodedJWT> jwtVerifier;
	
	@Autowired
	private RestProviderDAO dao;
	
	@BeforeEach
	public void mock() throws AccessTokenException {
		AccessToken accessToken = AccessToken.newInstance("a", "b", Long.MAX_VALUE);
		when(provider.getAccessToken(false)).thenReturn(accessToken);
		when(provider.getAccessToken(true)).thenReturn(accessToken);
	}
	
	@Test
	public void contextLoads() {
		System.out.println("Got filter " + filter.getClass());
	}
	
	@Test
	public void testTokenIsValid(@PartnerAccessToken(organisationId = 1) String token) throws SigningKeyUnavailableException  {
		System.out.println(token);
		
		DecodedJWT verified = jwtVerifier.verify(token);
		
		System.out.println(verified.getClaims());
	}
	
}

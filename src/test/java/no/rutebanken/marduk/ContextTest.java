package no.rutebanken.marduk;

import org.entur.jwt.junit5.entur.test.PartnerAccessToken;
import org.entur.jwt.junit5.entur.test.PartnerAuthorizationServer;
import org.entur.jwt.spring.filter.JwtAuthenticationFilter;
import org.entur.jwt.verifier.JwtVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import com.auth0.jwt.interfaces.DecodedJWT;

@PartnerAuthorizationServer
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class ContextTest {

	@Autowired
	private JwtAuthenticationFilter<?> filter;
	
	@Autowired
	JwtVerifier<DecodedJWT> jwtVerifier;
	
	@Test
	public void contextLoads() {
		System.out.println("Got filter " + filter.getClass());
	}
	
	@Test
	public void testTokenIsValid(@PartnerAccessToken(organisationId = 1) String token)  {
		System.out.println(token);
		
		DecodedJWT verified = jwtVerifier.verify(token);
		
		System.out.println(verified.getClaims());
		
		
	}
}

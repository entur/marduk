package no.rutebanken.marduk.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.util.Arrays;

import org.entur.jwt.junit5.entur.test.PartnerAccessToken;
import org.entur.jwt.junit5.entur.test.PartnerAuthorizationServer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
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

import static io.restassured.RestAssured.*;
import static io.restassured.matcher.RestAssuredMatchers.*;
import static org.hamcrest.Matchers.*;

@PartnerAuthorizationServer
@ActiveProfiles({"default", "in-memory-blobstore", "google-pubsub-emulator"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestApp.class)
public class AdminRestTest {

    @LocalServerPort
    public int port;

	@Test
	public void testThatSwaggerSpecificationIsAvailableWithoutAuthentication() throws Exception {
		URI uri = new URI("http://localhost:" + port + "/services/swagger.json");

	    when()
	    	.get(uri)
	    .then()
	       	.assertThat()
	       	.statusCode(200);
	}
	
	@Test
	public void testTimeTableAdmin(@PartnerAccessToken(organisationId = 1) String token) throws Exception {
		URI uri = new URI("http://localhost:" + port + "/services/timetable_admin/export/files");

		 given()
		 	.header("Authorization", token)
	    .when()
	    	.get(uri)
	    .then()
        	.assertThat()
        	.statusCode(200);
	    
	}	

	@Test
	public void testTimeTableAdminWithoutToken() throws Exception {
		URI uri = new URI("http://localhost:" + port + "/services/timetable_admin/export/files");

	    when()
	        .get(uri)
	    .then()
	        .assertThat()
	        .statusCode(403);
	}	

}

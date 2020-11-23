package no.rutebanken.marduk.security;

import no.rutebanken.marduk.TestApp;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.rutebanken.helper.organisation.AuthorizationConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestApp.class)
class AuthorizationServiceTest {

    @Autowired
    private AuthorizationService authorizationService;


    @Test
    void testVerifyAtLeastOne() {
        AuthorizationClaim authorizationClaim = new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN);
        Assertions.assertDoesNotThrow(() -> authorizationService.verifyAtLeastOne(authorizationClaim));
    }

}

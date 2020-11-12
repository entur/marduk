package no.rutebanken.marduk.security.oauth2;

import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.TestApp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestApp.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MultiIssuerAuthenticationManagerResolverTest extends MardukRouteBuilderIntegrationTestBase {

    @Autowired
    private MultiIssuerAuthenticationManagerResolver multiIssuerAuthenticationManagerResolver;

    @Test
    void testUnknownIssuer() {
        assertThrows(IllegalArgumentException.class, () -> multiIssuerAuthenticationManagerResolver.fromIssuer("unknown"));
    }

    @Test
    void testNullIssuer() {
        assertThrows(IllegalArgumentException.class, () -> multiIssuerAuthenticationManagerResolver.fromIssuer(null));
    }

}

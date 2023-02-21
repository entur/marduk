package no.rutebanken.marduk.security.oauth2;

import no.rutebanken.marduk.MardukSpringBootBaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertThrows;


@ActiveProfiles({"test", "default", "in-memory-blobstore", "google-pubsub-emulator", "google-pubsub-autocreate"})
class MultiIssuerAuthenticationManagerResolverTest extends MardukSpringBootBaseTest {

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

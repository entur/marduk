package no.rutebanken.marduk.rest;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Readiness endpoint.
 *
 * <p>Answers from a constant with no dependency on the database, GCS or PubSub, so it never fails on a
 * downstream outage. It reports on the HTTP layer itself: a pod whose {@code DispatcherServlet} is not
 * serving {@code /services/**} answers nothing here and drops out of the Service endpoints, which the
 * actuator health group does not catch.
 *
 * <p>Was {@code HealthRouteBuilder}, served by platform-http.
 */
@RestController
public class HealthController {

    /**
     * No {@code produces} on the mapping, for the same reason as every endpoint in
     * {@link AdminRestController}: it makes Spring answer 406 when the caller's {@code Accept} does not list
     * it. A Kubernetes httpGet probe sends no Accept header and would be fine either way, but anything that
     * asks for JSON - a monitoring check, a browser fetch - would not be.
     */
    @GetMapping("/services/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("OK");
    }
}

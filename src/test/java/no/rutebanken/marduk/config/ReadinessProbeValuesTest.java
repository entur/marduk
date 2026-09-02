package no.rutebanken.marduk.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards the readiness probe against moving back onto the application port.
 *
 * <p>camel-kubernetes builds the cluster member list from the pods whose Ready condition is true, so the readiness
 * probe decides leader election. Pointing it at a platform-http route on 8080 puts it behind the same connector and
 * task executor as the 150MB timetable uploads: on 2026-09-01 a stall there dropped a prd pod out of the member list.
 *
 * <p>Parsed rather than string-matched, unlike {@link ClusterServiceConfigMapTest}: values.yaml is plain YAML, while
 * the configmap is a Go template that no YAML parser accepts.
 */
class ReadinessProbeValuesTest {

    private static final Path VALUES = Path.of("helm/marduk/values.yaml");

    @Test
    void readinessProbeStaysOnTheManagementPort() throws IOException {
        Map<String, Object> httpGet = readinessHttpGet();

        assertEquals("/actuator/health/readiness", httpGet.get("path"),
                "The readiness probe (" + VALUES + ") must stay on an endpoint served outside Camel.");
        assertEquals(9001, httpGet.get("port"),
                "The readiness probe (" + VALUES + ") must stay on the management port. Camel derives cluster "
                        + "membership from pod readiness, so a probe sharing the application connector with large "
                        + "uploads turns a request stall into a leader election.");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readinessHttpGet() throws IOException {
        Object node = new Yaml().load(Files.readString(VALUES));
        for (String key : new String[]{"common", "container", "probes", "spec", "readinessProbe", "httpGet"}) {
            node = ((Map<String, Object>) node).get(key);
            if (node == null) {
                throw new AssertionError("No readinessProbe.httpGet in " + VALUES + ", missing key: " + key);
            }
        }
        return (Map<String, Object>) node;
    }
}

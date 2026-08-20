package no.rutebanken.marduk.leader;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * The fabric8 client is built reflectively, so a missing implementation artifact is invisible until a pod
 * tries to elect a leader.
 *
 * <p>This is not hypothetical: {@code kubernetes-client} arrived transitively through
 * {@code camel-kubernetes-starter}, and removing that starter left {@code kubernetes-client-api} behind on
 * its own. Everything compiled, all tests passed, and the first pod to start crash-looped on
 * {@code ClassNotFoundException: KubernetesClientImpl}. No test builds a real client - the test profile uses
 * the single-node leader election - so nothing else would notice.
 */
class KubernetesClientOnTheClasspathTest {

    @Test
    void theClientImplementationIsOnTheClasspathAndNotOnlyItsApi() {
        assertDoesNotThrow(() -> Class.forName("io.fabric8.kubernetes.client.impl.KubernetesClientImpl"),
                "io.fabric8:kubernetes-client is missing; KubernetesLeaderElection cannot build a client");
    }

    @Test
    void anHttpClientImplementationIsOnTheClasspathToo() {
        // The client resolves its HTTP transport through the ServiceLoader; without one it fails the same way.
        assertDoesNotThrow(() -> Class.forName("io.fabric8.kubernetes.client.jdkhttp.JdkHttpClientFactory"),
                "io.fabric8:kubernetes-httpclient-jdk is missing; the client has no HTTP transport");
    }

    @Test
    void theApiTypeItselfResolves() {
        assertDoesNotThrow(KubernetesClient.class::getName);
    }
}

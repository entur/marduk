package no.rutebanken.marduk.leader;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.time.Duration;

/**
 * Wires the leader election that replaces {@code camel.cluster.kubernetes}.
 *
 * <p>Property names mirror the shape of the Camel ones so the ConfigMap reads similarly, but they are new
 * keys. The Camel ones stay in the ConfigMap untouched for this release: {@code camel.cluster.kubernetes.enabled}
 * in particular is what lets the previous version's {@code master:} routes start at all, and helm applies
 * the ConfigMap before the first pod is replaced.
 */
@Configuration
public class LeaderElectionConfiguration {

    static final String KUBERNETES_ENABLED = "marduk.leader.kubernetes.enabled";

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(KUBERNETES_ENABLED)
    KubernetesClient leaderElectionKubernetesClient() {
        return new KubernetesClientBuilder().build();
    }

    @Bean
    @ConditionalOnProperty(KUBERNETES_ENABLED)
    LeaderElection kubernetesLeaderElection(
            KubernetesClient leaderElectionKubernetesClient,
            @Value("${rutebanken.kubernetes.namespace:default}") String namespace,
            @Value("${marduk.leader.lease.name:marduk-leader}") String leaseName,
            // The pod name, which the chart already exposes; falls back to the hostname, as camel-master did.
            @Value("${HOSTNAME:${marduk.leader.identity:marduk}}") String identity,
            @Value("${marduk.leader.lease.duration.seconds:30}") long leaseDurationSeconds,
            @Value("${marduk.leader.renew.deadline.seconds:20}") long renewDeadlineSeconds,
            @Value("${marduk.leader.retry.period.seconds:5}") long retryPeriodSeconds) {
        return new KubernetesLeaderElection(
                leaderElectionKubernetesClient,
                namespace,
                leaseName,
                identity,
                Duration.ofSeconds(leaseDurationSeconds),
                Duration.ofSeconds(renewDeadlineSeconds),
                Duration.ofSeconds(retryPeriodSeconds));
    }

    /**
     * The fallback for tests and local runs, refused inside a cluster.
     *
     * <p>{@code matchIfMissing} is what makes a mistyped or dropped ConfigMap key land here, and this
     * implementation is always the leader: both replicas would then run every scheduled job and serve every
     * batch. That failure is silent, and duplicate merged exports and graph builds are the visible half of
     * it. So in a pod - {@code KUBERNETES_SERVICE_HOST} is set in every one - reaching here needs an
     * explicit {@code false}, which is how a single-replica deployment opts out. Crash-looping on a missing
     * key is loud; two leaders is not.
     */
    @Bean
    @ConditionalOnProperty(value = KUBERNETES_ENABLED, havingValue = "false", matchIfMissing = true)
    LeaderElection singleNodeLeaderElection(Environment environment) {
        boolean turnedOffOnPurpose = "false".equalsIgnoreCase(environment.getProperty(KUBERNETES_ENABLED));
        if (!turnedOffOnPurpose && environment.containsProperty("KUBERNETES_SERVICE_HOST")) {
            throw new IllegalStateException(KUBERNETES_ENABLED
                    + " is not set to false, and this is a Kubernetes pod. Refusing to start rather than "
                    + "make every replica the leader; set it to true, or to false if this really is a "
                    + "single instance.");
        }
        return new SingleNodeLeaderElection();
    }
}

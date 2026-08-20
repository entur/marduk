package no.rutebanken.marduk.leader;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderCallbacks;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectionConfig;
import io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.LeaseLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Leader election on a Kubernetes Lease, held by the pod whose hostname wins it.
 *
 * <p>Uses the same Lease API camel-master used underneath, so the existing RBAC covers it: the Role
 * already grants {@code create, get, update, list} on {@code coordination.k8s.io/leases}. Nothing needs
 * adding.
 *
 * <p>The Lease name is deliberately <em>not</em> one of the ten camel-master used. Sharing a name would
 * make old and new pods contend correctly during a rollout, which sounds better than it is: the two
 * implementations would have to agree on the record format, and a mismatch is silent. A distinct name
 * means both versions lead during the rollout, which is the same position antu accepted, and is only safe
 * because nothing gated on this keeps state in the leader's heap.
 */
public class KubernetesLeaderElection implements LeaderElection, SmartLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesLeaderElection.class);

    private final KubernetesClient kubernetesClient;
    private final String namespace;
    private final String leaseName;
    private final String identity;
    private final Duration leaseDuration;
    private final Duration renewDeadline;
    private final Duration retryPeriod;

    private final AtomicBoolean leader = new AtomicBoolean();
    private volatile CompletableFuture<?> election;
    private volatile boolean running;

    public KubernetesLeaderElection(
            KubernetesClient kubernetesClient,
            String namespace,
            String leaseName,
            String identity,
            Duration leaseDuration,
            Duration renewDeadline,
            Duration retryPeriod) {
        this.kubernetesClient = kubernetesClient;
        this.namespace = namespace;
        this.leaseName = leaseName;
        this.identity = identity;
        this.leaseDuration = leaseDuration;
        this.renewDeadline = renewDeadline;
        this.retryPeriod = retryPeriod;
    }

    @Override
    public boolean isLeader() {
        return leader.get();
    }

    @Override
    public void start() {
        LeaderElectionConfig config = new LeaderElectionConfig(
                new LeaseLock(namespace, leaseName, identity),
                leaseDuration,
                renewDeadline,
                retryPeriod,
                new LeaderCallbacks(
                        () -> {
                            leader.set(true);
                            LOGGER.info("Acquired the {} lease as {}", leaseName, identity);
                        },
                        () -> {
                            leader.set(false);
                            LOGGER.info("Lost the {} lease", leaseName);
                        },
                        newLeader -> LOGGER.info("Lease {} is now held by {}", leaseName, newLeader)),
                // Release the lease when this pod shuts down, so the surviving pod takes over in the retry
                // period rather than after the lease expires.
                true,
                leaseName);
        election = kubernetesClient.leaderElector().withConfig(config).build().start();
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        leader.set(false);
        CompletableFuture<?> current = election;
        if (current != null) {
            current.cancel(true);
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}

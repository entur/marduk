package no.rutebanken.marduk.leader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Always the leader.
 *
 * <p>For tests and local runs, where there is one instance. Replaces what
 * {@code camel-file-cluster-service} did in the same situations, minus the lock file.
 */
public class SingleNodeLeaderElection implements LeaderElection {

    private static final Logger LOGGER = LoggerFactory.getLogger(SingleNodeLeaderElection.class);

    public SingleNodeLeaderElection() {
        LOGGER.info("Kubernetes leader election is off; this instance always considers itself the leader");
    }

    @Override
    public boolean isLeader() {
        return true;
    }
}

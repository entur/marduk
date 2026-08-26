package no.rutebanken.marduk.kubernetes;

/**
 * The job did not reach a terminal state within the configured timeout. Unlike a Kubernetes client error, the job has
 * been deleted, so retrying rebuilds the graph from scratch instead of reattaching.
 */
public class KubernetesJobTimeoutException extends KubernetesJobRunnerException {

    public KubernetesJobTimeoutException(String message) {
        super(message);
    }
}

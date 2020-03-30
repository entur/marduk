package no.rutebanken.marduk.kubernetes;

public class KubernetesJobRunnerException extends RuntimeException {
    public KubernetesJobRunnerException(String message, Throwable cause) {
        super(message, cause);
    }

    public KubernetesJobRunnerException(String message) {
        super(message);
    }
}

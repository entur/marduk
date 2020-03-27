package no.rutebanken.marduk.routes.otp;

public class KubernetesJobGraphBuilderException extends RuntimeException {
    public KubernetesJobGraphBuilderException(String message, Throwable cause) {

        super(message, cause);
    }

    public KubernetesJobGraphBuilderException(String message) {
        super(message);
    }
}

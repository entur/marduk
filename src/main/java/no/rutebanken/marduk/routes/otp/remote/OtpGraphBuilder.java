package no.rutebanken.marduk.routes.otp.remote;

/**
 * Build an OTP graph.
 * @see KubernetesJobGraphBuilder creates a Kubernetes job to build the graph in a dedicated pod.
 * @see InVMGraphBuilder builds the graph locally for testing purpose.
 */
public interface OtpGraphBuilder {

    /**
     *
     * @param otpWorkDir the local directory that contains at least the OTP configuration file (build-config.json).
     * @param buildBaseGraph build a base graph if true.
     * @param timestamp a timestamp used for creating unique file and directory names.
     */
    void build(String otpWorkDir, boolean buildBaseGraph, String timestamp);
}

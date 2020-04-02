package no.rutebanken.marduk.routes.otp;

import io.fabric8.kubernetes.api.model.EnvVar;

import java.util.List;

/**
 * Base interface for OTP graph builders.
 */
public interface OtpGraphBuilder {

    /**
     * Build an OTP graph and store it in working directory.
     * Depending on the implementation the working directory can be either a local or a remote directory.
     * @param otpWorkDir the directory where the graph is saved.
     * @param timestamp a timestamp used for creating unique file and directory names.
     */
    void build(String otpWorkDir, String timestamp);
}

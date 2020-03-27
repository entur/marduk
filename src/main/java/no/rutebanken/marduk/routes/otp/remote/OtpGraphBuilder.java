package no.rutebanken.marduk.routes.otp.remote;

import io.fabric8.kubernetes.api.model.EnvVar;

import java.util.List;


public interface OtpGraphBuilder {

    /**
     *
     * @param timestamp a timestamp used for creating unique file and directory names.
     */
    void build(String otpWorkDir, String timestamp);
}

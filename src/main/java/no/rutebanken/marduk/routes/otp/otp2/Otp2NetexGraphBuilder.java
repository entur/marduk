
package no.rutebanken.marduk.routes.otp.otp2;

import io.fabric8.kubernetes.api.model.EnvVar;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;


/**
 * Build an OTP full graph (with transit data) based on NeTEx input data.
 */
@Component
public class Otp2NetexGraphBuilder extends AbstractOtp2GraphBuilder {

    @Value("${otp.graph.blobstore.subdirectory:graphs}")
    private String blobStoreGraphSubdirectory;

    protected List<EnvVar> getEnvVars(String otpWorkDir, boolean candidate) {
        String baseGraphPath = blobStoreGraphSubdirectory + "/street";
        return List.of(
                new EnvVar(OTP_GCS_WORK_DIR_ENV_VAR, otpWorkDir, null),
                new EnvVar(OTP_GRAPH_MODE, "--loadStreet", null),
                new EnvVar(OTP_GCS_BASE_GRAPH_PATH_ENV_VAR, baseGraphPath, null));
    }

    @Override
    protected String getJobNamePrefix() {
        return "otp2-graph-builder-netex";
    }

}

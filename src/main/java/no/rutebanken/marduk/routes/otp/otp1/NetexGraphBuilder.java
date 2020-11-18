
package no.rutebanken.marduk.routes.otp.otp1;

import io.fabric8.kubernetes.api.model.EnvVar;
import no.rutebanken.marduk.routes.otp.OtpGraphBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;


/**
 * Build an OTP full graph (with transit data) based on NeTEx input data.
 */
@Component
public class NetexGraphBuilder extends AbstractOtpGraphBuilder implements OtpGraphBuilder {

    @Value("${otp.graph.blobstore.subdirectory:graphs}")
    private String blobStoreGraphSubdirectory;

    protected List<EnvVar> getEnvVars(String otpWorkDir) {
        return List.of(
                new EnvVar(OTP_GCS_WORK_DIR_ENV_VAR, otpWorkDir, null),
                new EnvVar(OTP_SKIP_TRANSIT_ENV_VAR, "", null),
                new EnvVar(OTP_LOAD_BASE_GRAPH_ENV_VAR, "--loadBaseGraph", null),
                new EnvVar(OTP_GCS_BASE_GRAPH_DIR_ENV_VAR, blobStoreGraphSubdirectory, null));
    }

    @Override
    protected String getJobNamePrefix() {
        return "otp1-graph-builder-netex";
    }


}

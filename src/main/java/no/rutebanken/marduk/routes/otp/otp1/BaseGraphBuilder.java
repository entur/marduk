
package no.rutebanken.marduk.routes.otp.otp1;

import io.fabric8.kubernetes.api.model.EnvVar;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Build an OTP base graph.
 */
@Component
public class BaseGraphBuilder extends AbstractOtpGraphBuilder {

    protected List<EnvVar> getEnvVars(String otpWorkDir) {
        return List.of(
                new EnvVar(OTP_GCS_WORK_DIR_ENV_VAR, otpWorkDir, null),
                new EnvVar(OTP_SKIP_TRANSIT_ENV_VAR, "--skipTransit", null),
                new EnvVar(OTP_LOAD_BASE_GRAPH_ENV_VAR, "", null),
                new EnvVar(OTP_GCS_BASE_GRAPH_DIR_ENV_VAR, otpWorkDir, null));
    }

    @Override
    protected String getJobNamePrefix() {
        return "otp1-graph-builder-base";
    }


}

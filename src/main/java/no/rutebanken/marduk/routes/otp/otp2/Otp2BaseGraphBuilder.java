
package no.rutebanken.marduk.routes.otp.otp2;

import io.fabric8.kubernetes.api.model.EnvVar;
import org.springframework.stereotype.Component;

import java.util.List;

import static no.rutebanken.marduk.Constants.OTP2_BASE_GRAPH_CANDIDATE_OBJ;
import static no.rutebanken.marduk.Constants.OTP2_BASE_GRAPH_OBJ;


/**
 * Build an OTP2 base graph.
 */
@Component
public class Otp2BaseGraphBuilder extends AbstractOtp2GraphBuilder {

    protected List<EnvVar> getEnvVars(String otpWorkDir, boolean candidate) {
        final String baseGraphPath;
        if (candidate) {
            baseGraphPath = otpWorkDir + "/" + OTP2_BASE_GRAPH_CANDIDATE_OBJ;
        } else {
            baseGraphPath = otpWorkDir + "/" + OTP2_BASE_GRAPH_OBJ;
        }
        return List.of(
                new EnvVar(OTP_GCS_WORK_DIR_ENV_VAR, otpWorkDir, null),
                new EnvVar(OTP_GRAPH_MODE, "--buildStreet", null),
                new EnvVar(OTP_GCS_BASE_GRAPH_PATH_ENV_VAR, baseGraphPath, null));
    }

    @Override
    protected String getJobNamePrefix() {
        return "otp2-graph-builder-base";
    }


}

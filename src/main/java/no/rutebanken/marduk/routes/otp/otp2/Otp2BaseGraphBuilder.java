
package no.rutebanken.marduk.routes.otp.otp2;

import io.fabric8.kubernetes.api.model.EnvVar;
import no.rutebanken.marduk.routes.otp.OtpGraphBuilder;
import org.springframework.stereotype.Component;

import java.util.List;


/**
 * Build an OTP2 base graph.
 */
@Component
public class Otp2BaseGraphBuilder extends AbstractOtp2GraphBuilder implements OtpGraphBuilder {

    protected List<EnvVar> getEnvVars(String otpWorkDir) {
        return List.of(
                new EnvVar(OTP_GCS_WORK_DIR_ENV_VAR, otpWorkDir, null),
                new EnvVar(OTP_GRAPH_MODE, "--buildStreet", null),
                new EnvVar(OTP_GCS_BASE_GRAPH_DIR_ENV_VAR, otpWorkDir, null));
    }


}

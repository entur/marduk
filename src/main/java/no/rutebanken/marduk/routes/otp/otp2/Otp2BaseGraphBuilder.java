
package no.rutebanken.marduk.routes.otp.otp2;

import io.fabric8.kubernetes.api.model.EnvVar;
import no.rutebanken.marduk.kubernetes.KubernetesJobRunner;
import no.rutebanken.marduk.routes.otp.remote.OtpGraphBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;


/**
 * Build an OTP2 base graph.
 */
@Component
public class Otp2BaseGraphBuilder implements OtpGraphBuilder {

    private static final String OTP_GCS_WORK_DIR_ENV_VAR = "OTP_GCS_WORK_DIR";
    private static final String OTP_GCS_BASE_GRAPH_DIR_ENV_VAR = "OTP_GCS_BASE_GRAPH_DIR";
    private static final String OTP_GRAPH_MODE = "OTP_GRAPH_MODE";

    @Value("${otp2.graph.build.remote.kubernetes.cronjob:graph-builder-otp2}")
    private String graphBuilderCronJobName;

    @Autowired
    private KubernetesJobRunner kubernetesJobRunner;

    protected List<EnvVar> getEnvVars(String otpWorkDir) {
        return List.of(
                new EnvVar(OTP_GCS_WORK_DIR_ENV_VAR, otpWorkDir, null),
                new EnvVar(OTP_GRAPH_MODE, "--buildStreet", null),
                new EnvVar(OTP_GCS_BASE_GRAPH_DIR_ENV_VAR, otpWorkDir, null));
    }

    @Override
    public void build(String otpWorkDir, String timestamp) {
        kubernetesJobRunner.runJob(graphBuilderCronJobName, getEnvVars(otpWorkDir), timestamp);
    }

}

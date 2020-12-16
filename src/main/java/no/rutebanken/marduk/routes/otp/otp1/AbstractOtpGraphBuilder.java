package no.rutebanken.marduk.routes.otp.otp1;

import io.fabric8.kubernetes.api.model.EnvVar;
import no.rutebanken.marduk.kubernetes.KubernetesJobRunner;
import no.rutebanken.marduk.routes.otp.OtpGraphBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

/**
 * Base class for OTP graph builders.
 */
public abstract class AbstractOtpGraphBuilder implements OtpGraphBuilder {

    protected static final String OTP_GCS_WORK_DIR_ENV_VAR = "OTP_GCS_WORK_DIR";
    protected static final String OTP_GCS_BASE_GRAPH_DIR_ENV_VAR = "OTP_GCS_BASE_GRAPH_DIR";
    protected static final String OTP_SKIP_TRANSIT_ENV_VAR = "OTP_SKIP_TRANSIT";
    protected static final String OTP_LOAD_BASE_GRAPH_ENV_VAR = "OTP_LOAD_BASE_GRAPH";

    @Value("${otp.graph.build.remote.kubernetes.cronjob:graph-builder}")
    private String graphBuilderCronJobName;

    @Autowired
    private KubernetesJobRunner kubernetesJobRunner;

    @Override
    public void build(String otpWorkDir, String timestamp, boolean candidate) {
        kubernetesJobRunner.runJob(graphBuilderCronJobName, getJobNamePrefix(), getEnvVars(otpWorkDir), timestamp);
    }

    protected abstract List<EnvVar> getEnvVars(String otpWorkDir);

    protected abstract String getJobNamePrefix();
}

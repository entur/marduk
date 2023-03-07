package no.rutebanken.marduk.routes.otp.otp2;

import io.fabric8.kubernetes.api.model.EnvVar;
import no.rutebanken.marduk.kubernetes.KubernetesJobRunner;
import no.rutebanken.marduk.routes.otp.OtpGraphBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

/**
 * Base class for OTP2 graph builders.
 */
public abstract class AbstractOtp2GraphBuilder implements OtpGraphBuilder {

    protected static final String OTP_GCS_WORK_DIR_ENV_VAR = "OTP_GCS_WORK_DIR";
    protected static final String OTP_GCS_BASE_GRAPH_PATH_ENV_VAR = "OTP_GCS_BASE_GRAPH_PATH";
    protected static final String OTP_GRAPH_MODE = "OTP_GRAPH_MODE";

    @Value("${otp2.graph.build.remote.kubernetes.cronjob:graph-builder-otp2}")
    private String graphBuilderCronJobName;

    @Autowired
    private KubernetesJobRunner kubernetesJobRunner;

    @Override
    public void build(String otpWorkDir, String timestamp, boolean candidate) {
        String cronJobName;
        String jobNamePrefix;
        if(candidate) {
            cronJobName = graphBuilderCronJobName + "-candidate";
            jobNamePrefix = getJobNamePrefix() + "-candidate";
        } else {
            cronJobName = graphBuilderCronJobName;
            jobNamePrefix = getJobNamePrefix();
        }
        kubernetesJobRunner.runJob(cronJobName, jobNamePrefix, getEnvVars(otpWorkDir, candidate), timestamp);
    }

    protected abstract List<EnvVar> getEnvVars(String otpWorkDir, boolean candidate);

    protected abstract String getJobNamePrefix();

}


package no.rutebanken.marduk.routes.otp.remote;

import io.fabric8.kubernetes.api.model.EnvVar;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;


/**
 * Build the OTP graph in a standalone pod and wait until the pod terminates.
 * The pod is created by a Kubernetes job.
 * A Kubernetes CronJob is used as a template for the job.
 */
@Component
public class KubernetesJobOtp2GraphBuilder extends AbstractKubernetesJobGraphBuilder {

    private static final String OTP_GCS_WORK_DIR_ENV_VAR = "OTP_GCS_WORK_DIR";
    private static final String OTP_GCS_BASE_GRAPH_DIR_ENV_VAR = "OTP_GCS_BASE_GRAPH_DIR";
    private static final String OTP_GRAPH_MODE = "OTP_GRAPH_MODE";

    @Value("${otp2.graph.build.remote.kubernetes.cronjob:graph-builder-otp2}")
    private String graphBuilderCronJobName;

    @Value("${otp.graph.blobstore.subdirectory:graphs}")
    private String blobStoreGraphSubdirectory;

    @Override
    public String getGraphBuilderCronJobName() {
        return graphBuilderCronJobName;
    }

    @Override
    protected List<EnvVar> getEnvVars(String otpWorkDir, boolean buildBaseGraph) {
        List<EnvVar> envVars = new ArrayList<>();
        envVars.add(new EnvVar(OTP_GCS_WORK_DIR_ENV_VAR, otpWorkDir, null));
        envVars.add(new EnvVar(OTP_GRAPH_MODE, buildBaseGraph ? "--buildStreet" : "--loadStreet", null));

        if (buildBaseGraph) {
            envVars.add(new EnvVar(OTP_GCS_BASE_GRAPH_DIR_ENV_VAR, otpWorkDir, null));
        } else {
            envVars.add(new EnvVar(OTP_GCS_BASE_GRAPH_DIR_ENV_VAR, blobStoreGraphSubdirectory, null));
        }
        return envVars;
    }


}
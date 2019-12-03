
package no.rutebanken.marduk.routes.otp.remote;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.batch.CronJob;
import io.fabric8.kubernetes.api.model.batch.CronJobSpec;
import io.fabric8.kubernetes.api.model.batch.Job;
import io.fabric8.kubernetes.api.model.batch.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.JobSpec;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


@Component
@Profile("otp-kubernetes-job-graph-builder")
public class KubernetesJobGraphBuilder implements OtpGraphBuilder {

    private static final String OTP_GCS_WORK_DIR_ENV_VAR = "OTP_GCS_WORK_DIR";
    private static final String OTP_GCS_BASE_GRAPH_DIR_ENV_VAR = "OTP_GCS_BASE_GRAPH_DIR";
    private static final String OTP_SKIP_TRANSIT_ENV_VAR = "OTP_SKIP_TRANSIT";
    private static final String OTP_LOAD_BASE_GRAPH_ENV_VAR = "OTP_LOAD_BASE_GRAPH";

    @Value("${otp.graph.blobstore.subdirectory:graphs}")
    private String blobStoreGraphSubdirectory;

    @Value("${otp.graph.build.remote.kubernetes.namespace:default}")
    private String kubernetesNamespace;

    @Value("${otp.graph.build.remote.kubernetes.cronjob:graph-builder}")
    private String graphBuilderCronJobName;


    private static final Logger logger = LoggerFactory.getLogger(KubernetesJobGraphBuilder.class);

    @Override
    public void build(String otpWorkDir, boolean buildBaseGraph, String timestamp) {
        try (final KubernetesClient client = new DefaultKubernetesClient()) {

            CronJobSpec specTemplate = getCronJobSpecTemplate(client);
            String jobName = graphBuilderCronJobName + '-' + timestamp;

            logger.info("Creating Graph builder job with name {} ", jobName);
            Job job = buildJobfromCronJobSpecTemplate(specTemplate, jobName, otpWorkDir, buildBaseGraph);
            client.batch().jobs().inNamespace(kubernetesNamespace).create(job);


            final CountDownLatch watchLatch = new CountDownLatch(1);
            try (Watch watch = client.pods().inNamespace(kubernetesNamespace).withLabel("job-name", jobName).watch(new Watcher<Pod>() {
                @Override
                public void eventReceived(Action action, Pod pod) {
                    String podName = pod.getMetadata().getName();
                    logger.info("The Graph Builder pod {} is in phase {}.", podName, pod.getStatus().getPhase());
                    if (pod.getStatus().getPhase().equals("Succeeded")) {
                        logger.info("Log message received from Graph Builder pod {}: {}", podName, client.pods().inNamespace(kubernetesNamespace).withName(podName).getLog());
                        watchLatch.countDown();
                    }
                }

                @Override
                public void onClose(KubernetesClientException e) {
                    // Ignore
                }
            })) {
                watchLatch.await(120, TimeUnit.MINUTES);
            } catch (KubernetesClientException | InterruptedException e) {
                logger.info("Could not watch pod", e);
            }


        }
    }

    protected CronJobSpec getCronJobSpecTemplate(KubernetesClient client) {
        List<CronJob> matchingJobs = client.batch().cronjobs().inNamespace(kubernetesNamespace).withLabel("name", graphBuilderCronJobName).list().getItems();
        if (matchingJobs.isEmpty()) {
            throw new RuntimeException("Job " + graphBuilderCronJobName + " not found");
        }
        if (matchingJobs.size() > 1) {
            throw new RuntimeException("Found multiple jobs matching name=" + graphBuilderCronJobName);
        }
        return matchingJobs.get(0).getSpec();
    }


    protected Job buildJobfromCronJobSpecTemplate(CronJobSpec specTemplate, String jobName, String otpWorkDir, boolean buildBaseGraph) {

        JobSpec jobSpec = specTemplate.getJobTemplate().getSpec();

        List<EnvVar> envVars = new ArrayList<>();
        envVars.add(new EnvVar(OTP_GCS_WORK_DIR_ENV_VAR, otpWorkDir, null));
        envVars.add(new EnvVar(OTP_SKIP_TRANSIT_ENV_VAR, buildBaseGraph ? "true" : "false", null));
        envVars.add(new EnvVar(OTP_LOAD_BASE_GRAPH_ENV_VAR, buildBaseGraph ? "false" : "true", null));

        if (buildBaseGraph) {
            envVars.add(new EnvVar(OTP_GCS_BASE_GRAPH_DIR_ENV_VAR, otpWorkDir, null));
        } else {
            envVars.add(new EnvVar(OTP_GCS_BASE_GRAPH_DIR_ENV_VAR, blobStoreGraphSubdirectory, null));
        }

        Job job = new JobBuilder()
                .withSpec(jobSpec).
                        withNewMetadata()
                .withName(jobName)
                .endMetadata()
                .editOrNewSpec()
                .editTemplate()
                .editSpec()
                .editFirstContainer()
                .addAllToEnv(envVars)
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
        return job;

    }
}
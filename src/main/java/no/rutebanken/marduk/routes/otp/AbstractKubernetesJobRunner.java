package no.rutebanken.marduk.routes.otp;

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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public abstract class AbstractKubernetesJobRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractKubernetesJobRunner.class);

    @Value("${otp.graph.build.remote.kubernetes.namespace:default}")
    private String kubernetesNamespace;

    @Value("${otp.graph.build.remote.kubernetes.job.cleanup:true}")
    private boolean deleteJobAfterCompletion;

    @Value("${otp.graph.build.remote.kubernetes.timeout:9000}")
    private long jobTimeoutSecond;

    public void runJob(List<EnvVar> envVars, String timestamp) {
        try (final KubernetesClient client = new DefaultKubernetesClient()) {

            CronJobSpec specTemplate = getCronJobSpecTemplate(client);
            String jobName = getCronJobName() + '-' + timestamp;

            LOGGER.info("Creating Graph builder job with name {} ", jobName);

            Job job = buildJobfromCronJobSpecTemplate(specTemplate, jobName, envVars);
            client.batch().jobs().inNamespace(kubernetesNamespace).create(job);


            final CountDownLatch watchLatch = new CountDownLatch(1);
            try (Watch watch = client.pods().inNamespace(kubernetesNamespace).withLabel("job-name", jobName).watch(new Watcher<Pod>() {

                int backoffLimit = job.getSpec().getBackoffLimit();
                int podFailureCounter = 0;

                @Override
                public void eventReceived(Action action, Pod pod) {
                    String podName = pod.getMetadata().getName();
                    LOGGER.info("The Graph Builder pod {} is in phase {} (Action: {}).", podName, pod.getStatus().getPhase(), action.name());
                    if (pod.getStatus().getPhase().equals("Succeeded")) {
                        watchLatch.countDown();
                    }
                    if (pod.getStatus().getPhase().equals("Failed")) {
                        podFailureCounter++;
                        if (podFailureCounter > backoffLimit) {
                            watchLatch.countDown();
                        }
                    }
                }

                @Override
                public void onClose(KubernetesClientException e) {
                    if (e != null) {
                        throw new KubernetesJobGraphBuilderException("The Graph Builder job ended with an error", e);
                    }
                }
            })) {

                boolean jobCompletedBeforeTimeout = watchLatch.await(jobTimeoutSecond, TimeUnit.SECONDS);
                if (!jobCompletedBeforeTimeout) {
                    throw new KubernetesJobGraphBuilderException("Timeout while waiting for the Graph Builder job " + jobName + " to complete.");
                }

                Integer succeeded = client.batch().jobs().inNamespace(kubernetesNamespace).withName(jobName).get().getStatus().getSucceeded();
                boolean jobSucceeded = succeeded != null && succeeded > 0;
                if (jobSucceeded) {
                    LOGGER.info("The Graph Builder job {} completed successfully.", jobName);
                } else {
                    throw new KubernetesJobGraphBuilderException("The Graph Builder job " + jobName + " failed.");
                }


            } catch (KubernetesClientException e) {
                throw new KubernetesJobGraphBuilderException("Could not watch pod", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new KubernetesJobGraphBuilderException("Interrupted while watching pod", e);
            } finally {
                // Delete job after completion
                if (deleteJobAfterCompletion) {
                    LOGGER.info("Deleting job {} after completion.", jobName);
                    client.batch().jobs().inNamespace(kubernetesNamespace).delete(job);
                    LOGGER.info("Deleted job {} after completion.", jobName);
                }
            }
        }
    }

    protected CronJobSpec getCronJobSpecTemplate(KubernetesClient client) {
        List<CronJob> matchingJobs = client.batch().cronjobs().inNamespace(kubernetesNamespace).withLabel("app", getCronJobName()).list().getItems();
        if (matchingJobs.isEmpty()) {
            throw new KubernetesJobGraphBuilderException("Job with label=" + getCronJobName() + " not found in namespace " + kubernetesNamespace);
        }
        if (matchingJobs.size() > 1) {
            throw new KubernetesJobGraphBuilderException("Found multiple jobs matching label app=" + getCronJobName() + " in namespace " + kubernetesNamespace);
        }
        return matchingJobs.get(0).getSpec();
    }

    protected Job buildJobfromCronJobSpecTemplate(CronJobSpec specTemplate, String jobName, List<EnvVar> envVars) {

        JobSpec jobSpec = specTemplate.getJobTemplate().getSpec();
        return new JobBuilder()
                .withSpec(jobSpec)
                .withNewMetadata()
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
    }


    protected abstract String getCronJobName();

}

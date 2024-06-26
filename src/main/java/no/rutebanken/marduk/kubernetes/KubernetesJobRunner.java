package no.rutebanken.marduk.kubernetes;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.api.model.batch.v1.CronJobSpec;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpec;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
import io.fabric8.kubernetes.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Run a Kubernetes job.
 * Uses a Kubernetes CronJob as a template to instantiate the job.
 * Assumptions:
 * - the job contains only one container
 * - all parameters can be passed as environment variables at job creation time.
 */
@Component
public class KubernetesJobRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesJobRunner.class);

    @Value("${otp.graph.build.remote.kubernetes.namespace:default}")
    private String kubernetesNamespace;

    @Value("${otp.graph.build.remote.kubernetes.job.cleanup:true}")
    private boolean deleteJobAfterCompletion;

    @Value("${otp.graph.build.remote.kubernetes.timeout:9000}")
    private long jobTimeoutSecond;

    /**
     * Run a Kubernetes job
     *
     * @param cronJobName   name of the CronJob used as a template
     * @param jobNamePrefix prefix for the Kubernetes job name
     * @param envVars       environment variables to be provided to the job
     * @param timestamp     timestamp used to create a unique name for the Kubernetes job.
     */
    public void runJob(String cronJobName, String jobNamePrefix, List<EnvVar> envVars, String timestamp) {
        try (final KubernetesClient kubernetesClient =  new KubernetesClientBuilder().build()) {
            String jobName = jobNamePrefix + '-' + timestamp;

            final Job job = retrieveOrCreateJob(jobName, cronJobName, envVars, kubernetesClient);


            final CountDownLatch watchLatch = new CountDownLatch(1);
            MardukPodWatcher mardukPodWatcher = new MardukPodWatcher(job, watchLatch, jobName);
            try (Watch watch = kubernetesClient.pods().inNamespace(kubernetesNamespace).withLabel("job-name", jobName).watch(mardukPodWatcher)) {

                boolean jobCompletedBeforeTimeout = watchLatch.await(jobTimeoutSecond, TimeUnit.SECONDS);
                if (!jobCompletedBeforeTimeout) {
                    throw new KubernetesJobRunnerException("Timeout while waiting for the Graph Builder job " + jobName + " to complete.");
                }
                JobStatus status = kubernetesClient.batch().v1().jobs().inNamespace(kubernetesNamespace).withName(jobName).get().getStatus();
                LOGGER.debug("Kubernetes Job status on completion: {}", status);
                // test the pod status rather than the job status since the job status may be out of sync with the pod status
                if (mardukPodWatcher.isSucceeded()) {
                    LOGGER.info("The Graph Builder job {} completed successfully.", jobName);
                } else if (mardukPodWatcher.isKubernetesClientError()) {
                    throw new KubernetesJobRunnerException("Kubernetes client error while watching the Graph Builder job " + jobName);
                } else {
                    throw new KubernetesJobRunnerException("The Graph Builder job " + jobName + " failed.");
                }
            } catch (KubernetesClientException e) {
                throw new KubernetesJobRunnerException("Could not watch pod", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new KubernetesJobRunnerException("Interrupted while watching pod", e);
            } finally {
                // Delete job after completion unless there was a Kubernetes error that can be retried
                if (!mardukPodWatcher.isKubernetesClientError() && deleteJobAfterCompletion) {
                    LOGGER.info("Deleting job {} after completion.", jobName);
                    deleteKubernetesJob(kubernetesClient, job);
                    LOGGER.info("Deleted job {} after completion.", jobName);
                }
            }
        }
    }

    /**
     * Retrieve a job or create a new one.
     * If a job with the same name is already running (presumably created during a previous attempt that failed due to network
     * issues), this job is retrieved. Otherwise, a new job is created.
     *
     * @param jobName          the Kubernetes job name.
     * @param kubernetesClient the Kubernetes client.
     * @param cronJobName      the name of the Kubernetes cron job used as a template to create a new Kubernetes job.
     * @param envVars          environment variables to be provided to the job.
     * @return the Kubernetes job
     */
    private Job retrieveOrCreateJob(String jobName, String cronJobName, List<EnvVar> envVars, KubernetesClient kubernetesClient) {
        Job job = kubernetesClient.batch().v1().jobs().inNamespace(kubernetesNamespace).withName(jobName).get();
        if (job != null) {
            LOGGER.info("Reconnecting to existing Graph builder job with name {} ", jobName);
        } else {
            LOGGER.info("Creating Graph builder job with name {} ", jobName);
            CronJobSpec specTemplate = getCronJobSpecTemplate(cronJobName, kubernetesClient);
            job = buildJobFromCronJobSpecTemplate(specTemplate, jobName, envVars);
            kubernetesClient.batch().v1().jobs().inNamespace(kubernetesNamespace).resource(job).create();
        }
        return job;
    }

    private void deleteKubernetesJob(KubernetesClient kubernetesClient, Job job) {
        try {
            kubernetesClient.batch().v1().jobs().inNamespace(kubernetesNamespace).resource(job).delete();
        } catch (Exception e) {
            LOGGER.warn("Unable to delete Kubernetes job after completion", e);
        }
    }

    protected CronJobSpec getCronJobSpecTemplate(String cronJobName, KubernetesClient client) {
        CronJob matchingCronJob = client.batch().v1().cronjobs().inNamespace(kubernetesNamespace).withName(cronJobName).get();
        if (matchingCronJob == null) {
            throw new KubernetesJobRunnerException("Job with name=" + cronJobName + " not found in namespace " + kubernetesNamespace);
        }
        return matchingCronJob.getSpec();
    }

    protected Job buildJobFromCronJobSpecTemplate(CronJobSpec specTemplate, String jobName, List<EnvVar> envVars) {

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

    private static class MardukPodWatcher implements Watcher<Pod> {

        private final CountDownLatch watchLatch;
        private final String jobName;
        private final int backoffLimit;
        private final AtomicInteger podFailureCounter;
        private volatile boolean kubernetesClientError;
        private volatile boolean succeeded;

        public boolean isKubernetesClientError() {
            return kubernetesClientError;
        }

        public boolean isSucceeded() {
            return succeeded;
        }

        public MardukPodWatcher(Job job, CountDownLatch watchLatch, String jobName) {
            this.watchLatch = watchLatch;
            this.jobName = jobName;
            backoffLimit = job.getSpec().getBackoffLimit();
            podFailureCounter = new AtomicInteger(0);
        }

        @Override
        public void eventReceived(Action action, Pod pod) {
            String podName = pod.getMetadata().getName();
            LOGGER.info("The Graph Builder pod {} is in phase {} (Action: {}).", podName, pod.getStatus().getPhase(), action.name());
            if (pod.getStatus().getPhase().equals("Succeeded")) {
                succeeded = true;
                watchLatch.countDown();
            }
            // counting only actions of type "MODIFIED" since Kubernetes can send multiple events in the phase "Failed" (action=MODIFIED, action=DELETED)
            if (pod.getStatus().getPhase().equals("Failed") && action.name().equals("MODIFIED")) {
                if (podFailureCounter.incrementAndGet() >= backoffLimit) {
                    LOGGER.error("The Graph Builder job {} failed (reason: {}) after {} retries, exceeding the backoff limit. Giving up.", jobName, pod.getStatus().getReason(), podFailureCounter);
                    watchLatch.countDown();
                } else {
                    LOGGER.warn("The Graph Builder job {} failed (reason: {}), retrying {}/{}", jobName, pod.getStatus().getReason(), podFailureCounter, backoffLimit);
                }
            }
        }

        @Override
        public void onClose(WatcherException cause) {
            if (cause != null) {
                LOGGER.warn("Kubernetes client error while watching the Graph Builder job {}. Trying to reconnect...", jobName, cause);
                kubernetesClientError = true;
                watchLatch.countDown();
            }
        }
    }
}

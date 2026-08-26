package no.rutebanken.marduk.kubernetes;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.api.model.batch.v1.CronJobSpec;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobCondition;
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

    @Value("${otp.graph.build.remote.kubernetes.timeout:5400}")
    private long jobTimeoutSecond;

    @Value("${otp.graph.build.remote.kubernetes.poll.interval:60}")
    private long pollIntervalSecond;

    /**
     * Run a Kubernetes job
     *
     * @param cronJobName   name of the CronJob used as a template
     * @param jobNamePrefix prefix for the Kubernetes job name
     * @param envVars       environment variables to be provided to the job
     * @param timestamp     timestamp used to create a unique name for the Kubernetes job.
     */
    public void runJob(String cronJobName, String jobNamePrefix, List<EnvVar> envVars, String timestamp) {
        try (final KubernetesClient kubernetesClient = createKubernetesClient()) {
            String jobName = jobNamePrefix + '-' + timestamp;

            final Job job = retrieveOrCreateJob(jobName, cronJobName, envVars, kubernetesClient);


            final CountDownLatch watchLatch = new CountDownLatch(1);
            MardukPodWatcher mardukPodWatcher = new MardukPodWatcher(job, watchLatch, jobName);
            try (Watch watch = kubernetesClient.pods().inNamespace(kubernetesNamespace).withLabel("job-name", jobName).watch(mardukPodWatcher)) {

                awaitJobCompletion(kubernetesClient, jobName, mardukPodWatcher);

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
                cleanUpKubernetesJob(kubernetesClient, job, jobName, mardukPodWatcher);
            }
        }
    }

    protected KubernetesClient createKubernetesClient() {
        return new KubernetesClientBuilder().build();
    }

    /**
     * The poll is a backstop against a watch that stops delivering events without failing: fabric8 7.7.0 never applies
     * websocketPingInterval, so a watch connection dropped by an intermediary during a long silent stretch raises no
     * error, schedules no reconnect, and would otherwise leave the caller blocked for the whole job timeout.
     */
    private void awaitJobCompletion(KubernetesClient kubernetesClient, String jobName, MardukPodWatcher mardukPodWatcher) throws InterruptedException {
        long startTime = System.nanoTime();
        long deadline = startTime + TimeUnit.SECONDS.toNanos(jobTimeoutSecond);
        long pollIntervalNanos = TimeUnit.SECONDS.toNanos(pollIntervalSecond);
        while (true) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                throw new KubernetesJobTimeoutException("Timeout while waiting for the Graph Builder job " + jobName + " to complete.");
            }
            if (mardukPodWatcher.awaitTerminalState(Math.min(pollIntervalNanos, remainingNanos))) {
                return;
            }
            if (pollJobStatus(kubernetesClient, jobName, mardukPodWatcher)) {
                long elapsedSecond = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime);
                LOGGER.warn("The Graph Builder job {} reached a terminal state detected via status poll after {}s, the pod watch did not deliver the terminal event.", jobName, elapsedSecond);
                return;
            }
        }
    }

    /**
     * @return true if the job reached a terminal state.
     */
    private boolean pollJobStatus(KubernetesClient kubernetesClient, String jobName, MardukPodWatcher mardukPodWatcher) {
        Job job;
        try {
            job = kubernetesClient.batch().v1().jobs().inNamespace(kubernetesNamespace).withName(jobName).get();
        } catch (KubernetesClientException e) {
            // transient API errors must not abort the wait, the next poll retries
            LOGGER.warn("Unable to poll the status of the Graph Builder job {}, retrying at the next poll interval.", jobName, e);
            return false;
        }
        if (job == null) {
            throw new KubernetesJobRunnerException("The Graph Builder job " + jobName + " no longer exists in namespace " + kubernetesNamespace);
        }
        JobStatus status = job.getStatus();
        if (status == null) {
            return false;
        }
        // Kubernetes reports the outcome as a condition. It is the only signal for a job failed by activeDeadlineSeconds
        // or a pod failure policy, where the failure counter can still be within the backoff limit.
        JobCondition terminalCondition = terminalCondition(status);
        if (terminalCondition != null) {
            if ("Complete".equals(terminalCondition.getType())) {
                mardukPodWatcher.markSucceeded();
            } else {
                LOGGER.error("The Graph Builder job {} failed (reason: {}, message: {}). Giving up.", jobName, terminalCondition.getReason(), terminalCondition.getMessage());
                mardukPodWatcher.markFailed();
            }
            return true;
        }
        if (status.getSucceeded() != null && status.getSucceeded() >= 1) {
            mardukPodWatcher.markSucceeded();
            return true;
        }
        if (status.getFailed() != null && mardukPodWatcher.isFailureBudgetExhausted(status.getFailed())) {
            LOGGER.error("The Graph Builder job {} failed {} times, exceeding the backoff limit. Giving up.", jobName, status.getFailed());
            mardukPodWatcher.markFailed();
            return true;
        }
        return false;
    }

    private static JobCondition terminalCondition(JobStatus status) {
        if (status.getConditions() == null) {
            return null;
        }
        return status.getConditions().stream()
                .filter(condition -> "True".equals(condition.getStatus()))
                .filter(condition -> "Complete".equals(condition.getType()) || "Failed".equals(condition.getType()))
                .findFirst()
                .orElse(null);
    }

    private void cleanUpKubernetesJob(KubernetesClient kubernetesClient, Job job, String jobName, MardukPodWatcher mardukPodWatcher) {
        if (!deleteJobAfterCompletion) {
            return;
        }
        // a Kubernetes client error can be retried, the job must be left in place so that retrieveOrCreateJob() can reattach to it
        if (mardukPodWatcher.isKubernetesClientError()) {
            return;
        }
        if (Thread.currentThread().isInterrupted()) {
            LOGGER.warn("Orphaned Kubernetes job {} in namespace {} : the current thread was interrupted, skipping the deletion. The job must be deleted manually.", jobName, kubernetesNamespace);
            return;
        }
        LOGGER.info("Deleting job {} after completion.", jobName);
        deleteKubernetesJob(kubernetesClient, job, jobName);
        LOGGER.info("Deleted job {} after completion.", jobName);
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

    private void deleteKubernetesJob(KubernetesClient kubernetesClient, Job job, String jobName) {
        try {
            kubernetesClient.batch().v1().jobs().inNamespace(kubernetesNamespace).resource(job).delete();
        } catch (Exception e) {
            LOGGER.warn("Orphaned Kubernetes job {} in namespace {} : the deletion after completion failed. The job must be deleted manually.", jobName, kubernetesNamespace, e);
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

        /**
         * backoffLimit is a retry count, so Kubernetes allows backoffLimit + 1 attempts and fails the job only once
         * status.failed exceeds it.
         */
        public boolean isFailureBudgetExhausted(int failures) {
            return failures > backoffLimit;
        }

        public boolean awaitTerminalState(long timeoutNanos) throws InterruptedException {
            return watchLatch.await(timeoutNanos, TimeUnit.NANOSECONDS);
        }

        public void markSucceeded() {
            succeeded = true;
            watchLatch.countDown();
        }

        /**
         * Release the wait without setting {@link #succeeded}, so the caller reports a failure.
         */
        public void markFailed() {
            watchLatch.countDown();
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
                if (isFailureBudgetExhausted(podFailureCounter.incrementAndGet())) {
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

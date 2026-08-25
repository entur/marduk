package no.rutebanken.marduk.kubernetes;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.BatchAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.ScalableResource;
import io.fabric8.kubernetes.client.dsl.V1BatchAPIGroupDSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KubernetesJobRunnerTest {

    private static final String NAMESPACE = "marduk";
    private static final String CRON_JOB_NAME = "otp2-graph-builder-netex";
    private static final String JOB_NAME_PREFIX = "otp2-graph-builder-netex";
    private static final String TIMESTAMP = "20260825092834774";
    private static final String JOB_NAME = JOB_NAME_PREFIX + '-' + TIMESTAMP;
    private static final int BACKOFF_LIMIT = 2;
    private static final String POLL_COMPLETION_LOG = "detected via status poll";

    private KubernetesClient kubernetesClient;
    private ScalableResource<Job> namedJobResource;
    private ScalableResource<Job> deletableJobResource;
    private FilterWatchListDeletable<Pod, PodList, PodResource> podsOfJob;
    private KubernetesJobRunner jobRunner;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        kubernetesClient = mock(KubernetesClient.class);
        stubKubernetesDsl();
        jobRunner = new KubernetesJobRunner() {
            @Override
            protected KubernetesClient createKubernetesClient() {
                return kubernetesClient;
            }
        };
        ReflectionTestUtils.setField(jobRunner, "kubernetesNamespace", NAMESPACE);
        ReflectionTestUtils.setField(jobRunner, "deleteJobAfterCompletion", true);
        ReflectionTestUtils.setField(jobRunner, "jobTimeoutSecond", 10L);
        ReflectionTestUtils.setField(jobRunner, "pollIntervalSecond", 1L);

        logger = (Logger) LoggerFactory.getLogger(KubernetesJobRunner.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logAppender);
    }

    @Test
    void watchStallsAndStatusPollDetectsCompletion() {
        Job runningJob = job(null, null);
        stubJobLookup(runningJob, job(1, null));
        // the watch reports progress and then goes silent without delivering the terminal event
        stubWatch(watcher -> {
            watcher.eventReceived(Watcher.Action.ADDED, pod("Pending"));
            watcher.eventReceived(Watcher.Action.MODIFIED, pod("Running"));
        });

        assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> jobRunner.runJob(CRON_JOB_NAME, JOB_NAME_PREFIX, List.of(), TIMESTAMP));

        assertTrue(loggedAtWarn(POLL_COMPLETION_LOG), "the poll-detected completion should be logged at WARN");
        verify(deletableJobResource).delete();
    }

    @Test
    void watchDeliversTerminalSucceededEvent() {
        Job job = job(1, null);
        stubJobLookup(job, job);
        stubWatch(watcher -> {
            watcher.eventReceived(Watcher.Action.MODIFIED, pod("Running"));
            watcher.eventReceived(Watcher.Action.MODIFIED, pod("Succeeded"));
        });

        assertDoesNotThrow(() -> jobRunner.runJob(CRON_JOB_NAME, JOB_NAME_PREFIX, List.of(), TIMESTAMP));

        assertFalse(loggedAtWarn(POLL_COMPLETION_LOG), "the status poll should not run when the watch delivers the terminal event");
        verify(namedJobResource, times(1)).get();
        verify(deletableJobResource).delete();
    }

    @Test
    void jobFailingBeyondBackoffLimitIsReportedAndDeleted() {
        Job job = job(null, null);
        stubJobLookup(job, job);
        stubWatch(watcher -> {
            for (int i = 0; i < BACKOFF_LIMIT + 1; i++) {
                watcher.eventReceived(Watcher.Action.MODIFIED, pod("Failed"));
            }
        });

        KubernetesJobRunnerException exception = assertThrows(KubernetesJobRunnerException.class,
                () -> jobRunner.runJob(CRON_JOB_NAME, JOB_NAME_PREFIX, List.of(), TIMESTAMP));

        assertTrue(exception.getMessage().contains("failed"), exception.getMessage());
        assertFalse(loggedAtWarn(POLL_COMPLETION_LOG), "the watch, not the poll, should detect the failure");
        verify(deletableJobResource).delete();
    }

    @Test
    void watchErrorLeavesTheJobInPlaceForARetry() {
        Job job = job(null, null);
        stubJobLookup(job, job);
        stubWatch(watcher -> watcher.onClose(new WatcherException("connection reset")));

        KubernetesJobRunnerException exception = assertThrows(KubernetesJobRunnerException.class,
                () -> jobRunner.runJob(CRON_JOB_NAME, JOB_NAME_PREFIX, List.of(), TIMESTAMP));

        assertTrue(exception.getMessage().contains("Kubernetes client error"), exception.getMessage());
        verify(deletableJobResource, never()).delete();
    }

    @Test
    void timeoutIsReportedWhenNoTerminalStateIsReached() {
        ReflectionTestUtils.setField(jobRunner, "jobTimeoutSecond", 2L);
        Job job = job(null, null);
        stubJobLookup(job, job);
        stubWatch(watcher -> watcher.eventReceived(Watcher.Action.MODIFIED, pod("Running")));

        // a distinct type, so the graph build routes can suppress redelivery on timeout only
        KubernetesJobTimeoutException exception = assertThrows(KubernetesJobTimeoutException.class,
                () -> jobRunner.runJob(CRON_JOB_NAME, JOB_NAME_PREFIX, List.of(), TIMESTAMP));

        assertTrue(exception.getMessage().startsWith("Timeout while waiting for the Graph Builder job"), exception.getMessage());
    }

    @Test
    void aRetryStillWithinTheBackoffLimitDoesNotEndTheJob() {
        // backoffLimit failures leave one attempt, which succeeds
        stubJobLookup(job(null, null), job(1, null));
        stubWatch(watcher -> {
            for (int i = 0; i < BACKOFF_LIMIT; i++) {
                watcher.eventReceived(Watcher.Action.MODIFIED, pod("Failed"));
            }
        });

        assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> jobRunner.runJob(CRON_JOB_NAME, JOB_NAME_PREFIX, List.of(), TIMESTAMP));

        verify(deletableJobResource).delete();
    }

    @Test
    void transientPollFailureIsRetriedAtTheNextInterval() {
        Job runningJob = job(null, null);
        when(namedJobResource.get())
                .thenReturn(runningJob)
                .thenThrow(new KubernetesClientException("the API server is briefly unavailable"))
                .thenReturn(job(1, null));
        stubWatch(watcher -> watcher.eventReceived(Watcher.Action.MODIFIED, pod("Running")));

        assertTimeoutPreemptively(Duration.ofSeconds(6),
                () -> jobRunner.runJob(CRON_JOB_NAME, JOB_NAME_PREFIX, List.of(), TIMESTAMP));

        assertTrue(loggedAtWarn("Unable to poll the status"), "the transient poll failure should be logged at WARN");
        assertTrue(loggedAtWarn(POLL_COMPLETION_LOG), "the next poll should detect the completion");
    }

    @Test
    void jobDeletedWhileRunningIsReportedDistinctly() {
        stubJobLookup(job(null, null), null);
        stubWatch(watcher -> watcher.eventReceived(Watcher.Action.MODIFIED, pod("Running")));

        KubernetesJobRunnerException exception = assertThrows(KubernetesJobRunnerException.class,
                () -> jobRunner.runJob(CRON_JOB_NAME, JOB_NAME_PREFIX, List.of(), TIMESTAMP));

        assertTrue(exception.getMessage().contains("no longer exists"), exception.getMessage());
    }

    @Test
    void failureToDeleteTheJobDoesNotMaskThePrimaryException() {
        Job job = job(null, null);
        stubJobLookup(job, job);
        stubWatch(watcher -> {
            for (int i = 0; i < BACKOFF_LIMIT + 1; i++) {
                watcher.eventReceived(Watcher.Action.MODIFIED, pod("Failed"));
            }
        });
        when(deletableJobResource.delete()).thenThrow(new KubernetesClientException("the job is already gone"));

        KubernetesJobRunnerException exception = assertThrows(KubernetesJobRunnerException.class,
                () -> jobRunner.runJob(CRON_JOB_NAME, JOB_NAME_PREFIX, List.of(), TIMESTAMP));

        assertTrue(exception.getMessage().contains("failed"), exception.getMessage());
        assertTrue(loggedAtWarn("Orphaned Kubernetes job"), "the job left behind should be logged at WARN");
    }

    @SuppressWarnings("unchecked")
    private void stubKubernetesDsl() {
        BatchAPIGroupDSL batch = mock(BatchAPIGroupDSL.class);
        V1BatchAPIGroupDSL batchV1 = mock(V1BatchAPIGroupDSL.class);
        MixedOperation<Job, JobList, ScalableResource<Job>> jobs = mock(MixedOperation.class);
        NonNamespaceOperation<Job, JobList, ScalableResource<Job>> jobsInNamespace = mock(NonNamespaceOperation.class);
        namedJobResource = mock(ScalableResource.class);
        deletableJobResource = mock(ScalableResource.class);
        when(kubernetesClient.batch()).thenReturn(batch);
        when(batch.v1()).thenReturn(batchV1);
        when(batchV1.jobs()).thenReturn(jobs);
        when(jobs.inNamespace(NAMESPACE)).thenReturn(jobsInNamespace);
        when(jobsInNamespace.withName(JOB_NAME)).thenReturn(namedJobResource);
        when(jobsInNamespace.resource(any(Job.class))).thenReturn(deletableJobResource);

        MixedOperation<Pod, PodList, PodResource> pods = mock(MixedOperation.class);
        NonNamespaceOperation<Pod, PodList, PodResource> podsInNamespace = mock(NonNamespaceOperation.class);
        podsOfJob = mock(FilterWatchListDeletable.class);
        when(kubernetesClient.pods()).thenReturn(pods);
        when(pods.inNamespace(NAMESPACE)).thenReturn(podsInNamespace);
        when(podsInNamespace.withLabel("job-name", JOB_NAME)).thenReturn(podsOfJob);
    }

    /**
     * Stub the job lookup so that runJob() reattaches to an existing job, then observes the subsequent status.
     */
    private void stubJobLookup(Job firstLookup, Job subsequentLookups) {
        when(namedJobResource.get()).thenReturn(firstLookup, subsequentLookups);
    }

    private void stubWatch(Consumer<Watcher<Pod>> eventSequence) {
        Answer<Watch> answer = invocation -> {
            Watcher<Pod> watcher = invocation.getArgument(0);
            eventSequence.accept(watcher);
            return mock(Watch.class);
        };
        when(podsOfJob.watch(any())).thenAnswer(answer);
    }

    private boolean loggedAtWarn(String fragment) {
        return logAppender.list.stream()
                .filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN))
                .anyMatch(event -> event.getFormattedMessage().contains(fragment));
    }

    private static Job job(Integer succeeded, Integer failed) {
        return new JobBuilder()
                .withNewMetadata().withName(JOB_NAME).endMetadata()
                .withNewSpec().withBackoffLimit(BACKOFF_LIMIT).endSpec()
                .withNewStatus().withSucceeded(succeeded).withFailed(failed).endStatus()
                .build();
    }

    private static Pod pod(String phase) {
        return new PodBuilder()
                .withNewMetadata().withName(JOB_NAME + "-abcde").endMetadata()
                .withNewStatus().withPhase(phase).endStatus()
                .build();
    }
}

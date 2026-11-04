package no.rutebanken.marduk.otp;

import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import no.rutebanken.marduk.kubernetes.KubernetesJobRunnerException;
import no.rutebanken.marduk.leader.LeaderElection;
import no.rutebanken.marduk.pipeline.MardukMdc;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubPublisher;
import no.rutebanken.marduk.pubsub.MardukQueues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Triggers a candidate base graph build when the candidate graph builder CronJob changes.
 *
 * <p>Replaces {@code MonitorCandidateBaseGraphBuilderVersionRouteBuilder}. "Changed" means the CronJob's
 * {@code resourceVersion} differs from the one seen last, which is held in memory.
 *
 * <p>That in-memory version has a consequence worth knowing, and it is not new: the first check after a
 * process starts has nothing to compare against and therefore always reports a new version, so a restart or
 * a change of leader triggers one candidate build. Under camel-master the same thing happened whenever
 * leadership moved to a pod that had not run the check before. Cheap enough that it is preserved rather
 * than made durable.
 */
@Component
public class CandidateBaseGraphBuilderMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(CandidateBaseGraphBuilderMonitor.class);

    private final MardukPubSubPublisher publisher;
    private final LeaderElection leaderElection;
    private final String kubernetesNamespace;
    private final String candidateGraphBuilderCronJobName;

    private String currentResourceVersion;

    public CandidateBaseGraphBuilderMonitor(
            MardukPubSubPublisher publisher,
            LeaderElection leaderElection,
            @Value("${otp.graph.build.remote.kubernetes.namespace:default}") String kubernetesNamespace,
            @Value("${otp2.graph.build.remote.kubernetes.cronjob:graph-builder-otp2}") String graphBuilderCronJobName) {
        this.publisher = publisher;
        this.leaderElection = leaderElection;
        this.kubernetesNamespace = kubernetesNamespace;
        this.candidateGraphBuilderCronJobName = graphBuilderCronJobName + "-candidate";
    }

    /**
     * Every five minutes.
     *
     * <p>The default is {@code 0 *<!-- -->/5 * * * ?}, not the {@code 0 /5 * * * ?} the quartz version used.
     * Quartz accepted a bare step; Spring rejects it and the context would fail at startup. This value has no
     * ConfigMap override, so the default is what actually runs. {@code CronScheduleTest} checks every
     * {@code @Scheduled} default for exactly this.
     */
    @Scheduled(cron = "${otp.graph.build.base.candidate.monitor.cron:0 */5 * * * ?}", zone = "Europe/Oslo")
    void checkForNewVersionOnSchedule() {
        if (!leaderElection.isLeader()) {
            LOGGER.debug("Not the leader, skipping the candidate base graph builder version check");
            return;
        }
        MardukMdc.clear();
        MardukMdc.setCorrelationId(UUID.randomUUID().toString());
        try {
            checkForNewVersion();
        } finally {
            MardukMdc.clear();
        }
    }

    /** Publishes a candidate build request if the builder has changed since the last check. */
    public void checkForNewVersion() {
        if (!isNewVersion()) {
            return;
        }
        LOGGER.info("There is a new version of the candidate base graph builder, triggering build.");
        publisher.publish(MardukQueues.OTP2_BASE_GRAPH_CANDIDATE_BUILD_QUEUE, new MardukMessage());
    }

    boolean isNewVersion() {
        String resourceVersion = candidateBuilderResourceVersion();
        LOGGER.debug("Current resource version is {}, new resource version is {}",
                currentResourceVersion, resourceVersion);
        if (currentResourceVersion == null || !currentResourceVersion.equals(resourceVersion)) {
            currentResourceVersion = resourceVersion;
            return true;
        }
        return false;
    }

    String candidateBuilderResourceVersion() {
        try (KubernetesClient kubernetesClient = new KubernetesClientBuilder().build()) {
            CronJob matchingCronJob = kubernetesClient.batch().v1().cronjobs()
                    .inNamespace(kubernetesNamespace).withName(candidateGraphBuilderCronJobName).get();
            if (matchingCronJob == null) {
                throw new KubernetesJobRunnerException("Job with name=" + candidateGraphBuilderCronJobName
                        + " not found in namespace " + kubernetesNamespace);
            }
            return matchingCronJob.getMetadata().getResourceVersion();
        }
    }
}

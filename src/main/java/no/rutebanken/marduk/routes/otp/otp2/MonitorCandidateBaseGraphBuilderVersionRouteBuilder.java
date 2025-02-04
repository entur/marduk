/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.routes.otp.otp2;

import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import no.rutebanken.marduk.kubernetes.KubernetesJobRunnerException;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Periodically check if the candidate base graph builder has been updated and trigger a candidate base
 * graph build if a new version is detected.
 */
@Component
public class MonitorCandidateBaseGraphBuilderVersionRouteBuilder extends BaseRouteBuilder {

    /**
     * Every 5 minutes by default.
     */
    private final String cronSchedule;

    private final String kubernetesNamespace;

    private final String candidateGraphBuilderCronJobName;

    private String currentCreationTimestamp;

    public MonitorCandidateBaseGraphBuilderVersionRouteBuilder(
            @Value("${otp.graph.build.base.candidate.monitor.cron.schedule:0+/5+*+*+*+?}") String cronSchedule,
            @Value("${otp.graph.build.remote.kubernetes.namespace:default}") String kubernetesNamespace,
            @Value("${otp2.graph.build.remote.kubernetes.cronjob:graph-builder-otp2}") String graphBuilderCronJobName) {
        this.cronSchedule = cronSchedule;
        this.kubernetesNamespace = kubernetesNamespace;
        this.candidateGraphBuilderCronJobName = graphBuilderCronJobName + "-candidate";
    }


    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("quartz://marduk/monitorBaseGraphBuilderCandidate?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .process(this::setNewCorrelationId)
                .log(LoggingLevel.INFO, correlation() + "Quartz triggers check version of candidate base graph builder.")
                .to("direct:checkCandidateBaseGraphVersion")
                .log(LoggingLevel.INFO, correlation() + "Check version of candidate base graph builder done.")
                .routeId("quartz-check-candidate-base-graph-version");

        from("direct:checkCandidateBaseGraphVersion")
            .filter(exchange -> isNewVersion())
            .log(LoggingLevel.INFO, correlation() + "There is a new version of the candidate base graph builder, triggering build.")
            .to("google-pubsub:{{marduk.pubsub.project.id}}:Otp2BaseGraphCandidateBuildQueue")
            .routeId("check-candidate-base-graph-version");
    }

    private boolean isNewVersion() {
        try (final KubernetesClient kubernetesClient = new KubernetesClientBuilder().build()) {
            CronJob matchingCronJob = kubernetesClient.batch().v1().cronjobs().inNamespace(kubernetesNamespace).withName(candidateGraphBuilderCronJobName).get();
            if (matchingCronJob == null) {
                throw new KubernetesJobRunnerException("Job with name=" + candidateGraphBuilderCronJobName + " not found in namespace " + kubernetesNamespace);
            }
            String creationTimestamp = matchingCronJob.getMetadata().getCreationTimestamp();
            if (currentCreationTimestamp == null || !currentCreationTimestamp.equals(creationTimestamp)) {
                currentCreationTimestamp = creationTimestamp;
                return true;
            }

            return false;

        }
    }

}

package no.rutebanken.marduk.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubConsumer;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.chouette.json.Parameters;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

/**
 * Asks Chouette to validate a dataspace.
 *
 * <p>Replaces {@code ChouetteValidationRouteBuilder}'s consumer. The requested level travels on the message
 * and is what the job's status events are reported under, so a level 1 and a level 2 validation of the same
 * dataspace are two jobs in nabu rather than one.
 */
@Component
public class ChouetteValidationConsumer extends MardukPubSubConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChouetteValidationConsumer.class);

    private final ChouetteClient chouetteClient;
    private final ProviderRepository providerRepository;
    private final JobEventPublisher jobEvents;
    private final ChouetteJobSubmission submission;

    public ChouetteValidationConsumer(
            ChouetteClient chouetteClient,
            ProviderRepository providerRepository,
            JobEventPublisher jobEvents,
            ChouetteJobSubmission submission) {
        this.chouetteClient = chouetteClient;
        this.providerRepository = providerRepository;
        this.jobEvents = jobEvents;
        this.submission = submission;
    }

    @Override
    protected String destination() {
        return MardukQueues.CHOUETTE_VALIDATION_QUEUE;
    }

    @Override
    protected void handle(MardukMessage message) {
        ensureCorrelationId(message);
        message.removeHeader(Constants.CHOUETTE_JOB_ID);
        LOGGER.info("Starting Chouette validation");
        jobEvents.reportProviderJob(message, builder -> builder.timetableAction(level(message))
                .state(JobEvent.State.PENDING));

        Long providerId = message.getHeader(PROVIDER_ID, Long.class);
        Provider provider = providerRepository.getProvider(providerId);
        String referential = provider == null ? null : provider.getChouetteInfo().getReferential();
        if (referential == null || referential.isBlank()) {
            // Asserted after reporting PENDING, as the route did, so an operator sees in nabu that
            // something was asked for and did not happen rather than nothing at all.
            LOGGER.warn("Unable to start Chouette validation for missing referential or providerId");
            jobEvents.reportProviderJob(message, builder -> builder.timetableAction(level(message))
                    .state(JobEvent.State.FAILED));
            return;
        }

        message.setHeader(CHOUETTE_REFERENTIAL, referential);
        String jobLocation = chouetteClient.postMultipart(
                "/chouette_iev/referentials/" + referential + "/validator",
                ChouetteMultipart.parameters(Parameters.getValidationParameters(provider)));

        submission.pollUntilDone(message, jobLocation,
                ChouetteValidationResultHandler.DESTINATION, level(message));
    }

    private static JobEvent.TimetableAction level(MardukMessage message) {
        return message.getHeader(CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL, JobEvent.TimetableAction.class);
    }
}

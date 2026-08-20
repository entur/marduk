package no.rutebanken.marduk.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubPublisher;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

/**
 * What happens once Chouette has finished copying a dataspace's data onwards.
 *
 * <p>A successful transfer hands the work to the dataspace it was copied into: from here on the message is
 * about that provider, and the level 2 validation runs there. The provider it came from is kept in
 * {@code ORIGINAL_PROVIDER_ID} so the job events can still be traced back to it.
 *
 * <p>Replaces {@code direct:processTransferExportResult} and
 * {@code direct:checkScheduledJobsBeforeTriggeringRBSpaceValidation}.
 */
@Component
public class ChouetteTransferResultHandler implements ChouetteJobResultHandler {

    static final String DESTINATION = "direct:processTransferExportResult";

    private static final Logger LOGGER = LoggerFactory.getLogger(ChouetteTransferResultHandler.class);

    private final ChouetteJobs chouetteJobs;
    private final ProviderRepository providerRepository;
    private final JobEventPublisher jobEvents;
    private final MardukPubSubPublisher publisher;

    public ChouetteTransferResultHandler(
            ChouetteJobs chouetteJobs,
            ProviderRepository providerRepository,
            JobEventPublisher jobEvents,
            MardukPubSubPublisher publisher) {
        this.chouetteJobs = chouetteJobs;
        this.providerRepository = providerRepository;
        this.jobEvents = jobEvents;
        this.publisher = publisher;
    }

    @Override
    public String destination() {
        return DESTINATION;
    }

    @Override
    public void handle(MardukMessage message) {
        String actionReport = message.getHeader("action_report_result", String.class);
        message.setBody("");

        if (!"OK".equals(actionReport)) {
            if ("NOK".equals(actionReport)) {
                LOGGER.info("Transfer failed");
            } else {
                LOGGER.error("Something went wrong on transfer");
            }
            report(message, JobEvent.State.FAILED);
            return;
        }

        report(message, JobEvent.State.OK);
        switchToTheDestinationDataspace(message);
        validateUnlessMoreImportsAreQueued(message);
    }

    /**
     * From here on the work belongs to the dataspace the data was copied into.
     *
     * <p>{@code ORIGINAL_PROVIDER_ID} is set only if it is not already there, so a chain of transfers still
     * points back at the provider the data came from originally.
     */
    private void switchToTheDestinationDataspace(MardukMessage message) {
        Provider provider = providerRepository.getProvider(message.getHeader(PROVIDER_ID, Long.class));
        if (message.getHeader(Constants.ORIGINAL_PROVIDER_ID) == null) {
            message.setHeader(Constants.ORIGINAL_PROVIDER_ID, message.getHeader(PROVIDER_ID));
        }
        message.setHeader(PROVIDER_ID, provider.getChouetteInfo().getMigrateDataToProvider());
    }

    private void validateUnlessMoreImportsAreQueued(MardukMessage message) {
        if (chouetteJobs.hasQueuedImports(message.getHeader(CHOUETTE_REFERENTIAL, String.class))) {
            LOGGER.info("Transfer ok, skipping validation as there are more import jobs active");
            return;
        }
        LOGGER.info("Transfer ok, triggering validation.");
        MardukMessage validation = message.copy()
                .setHeader(CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL,
                        JobEvent.TimetableAction.VALIDATION_LEVEL_2.name());
        validation.setBody("");
        publisher.publish(MardukQueues.CHOUETTE_VALIDATION_QUEUE, validation);
    }

    private void report(MardukMessage message, JobEvent.State state) {
        jobEvents.reportProviderJob(message, builder -> builder
                .timetableAction(JobEvent.TimetableAction.DATASPACE_TRANSFER).state(state));
    }
}

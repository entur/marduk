package no.rutebanken.marduk.chouette;

import no.rutebanken.marduk.Constants;
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
 * What happens once Chouette has finished validating a dataspace.
 *
 * <p>A clean validation moves the data on: to the next dataspace if this provider migrates its data onwards,
 * otherwise to a NeTEx export. Unless another import for the dataspace is still queued, in which case the
 * data is about to change again and neither is worth starting.
 *
 * <p>Replaces {@code direct:processValidationResult} and {@code direct:checkScheduledJobsBeforeTriggeringExport}.
 */
@Component
public class ChouetteValidationResultHandler implements ChouetteJobResultHandler {

    static final String DESTINATION = "direct:processValidationResult";

    private static final Logger LOGGER = LoggerFactory.getLogger(ChouetteValidationResultHandler.class);

    private static final String ACTION_REPORT_RESULT = "action_report_result";
    private static final String VALIDATION_REPORT_RESULT = "validation_report_result";

    private final ChouetteJobs chouetteJobs;
    private final ProviderRepository providerRepository;
    private final JobEventPublisher jobEvents;
    private final MardukPubSubPublisher publisher;

    public ChouetteValidationResultHandler(
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
        String actionReport = message.getHeader(ACTION_REPORT_RESULT, String.class);
        String validationReport = message.getHeader(VALIDATION_REPORT_RESULT, String.class);
        message.setBody("");

        if ("OK".equals(actionReport) && "OK".equals(validationReport)) {
            moveTheDataOn(message);
            report(message, JobEvent.State.OK);
            return;
        }
        if ("OK".equals(actionReport) && "NOK".equals(validationReport)) {
            LOGGER.info("Validation failed (processed ok, but timetable data is faulty)");
        } else {
            LOGGER.warn("Validation went wrong with error code {}",
                    message.getHeader(Constants.JOB_ERROR_CODE));
        }
        report(message, JobEvent.State.FAILED);
    }

    private void moveTheDataOn(MardukMessage message) {
        if (chouetteJobs.hasQueuedImports(message.getHeader(CHOUETTE_REFERENTIAL, String.class))) {
            LOGGER.info("Validation ok, skipping export as there are more import jobs active");
            return;
        }
        MardukMessage next = message.copy();
        next.setBody("");
        if (migratesDataOnwards(message)) {
            LOGGER.info("Validation ok, transfering data to next dataspace");
            publisher.publish(MardukQueues.CHOUETTE_TRANSFER_EXPORT_QUEUE, next);
            return;
        }
        LOGGER.info("Validation ok, triggering NeTEx export.");
        publisher.publish(MardukQueues.CHOUETTE_EXPORT_NETEX_QUEUE, next);
    }

    private boolean migratesDataOnwards(MardukMessage message) {
        return providerRepository.getProvider(message.getHeader(PROVIDER_ID, Long.class))
                .getChouetteInfo().getMigrateDataToProvider() != null;
    }

    private void report(MardukMessage message, JobEvent.State state) {
        jobEvents.reportProviderJob(message, builder -> builder
                .timetableAction(message.getHeader(CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL,
                        JobEvent.TimetableAction.class))
                .state(state));
    }
}

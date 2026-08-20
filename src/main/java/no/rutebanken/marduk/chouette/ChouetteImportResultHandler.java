package no.rutebanken.marduk.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pipeline.RetryPolicy;
import no.rutebanken.marduk.pubsub.MardukPubSubPublisher;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.routes.chouette.json.JobResponse;
import no.rutebanken.marduk.routes.chouette.json.Status;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_VERSION;

/**
 * What happens once Chouette has finished importing a dataset.
 *
 * <p>An import that both ran and validated cleanly keeps a copy of the dataset for nisaba and then triggers
 * validation - unless another import for the same dataspace is still queued, in which case the last one to
 * finish triggers it. Anything else is a failed import.
 *
 * <p>Replaces {@code direct:processImportResult} and the two routes it called.
 */
@Component
public class ChouetteImportResultHandler implements ChouetteJobResultHandler {

    static final String DESTINATION = "direct:processImportResult";

    private static final Logger LOGGER = LoggerFactory.getLogger(ChouetteImportResultHandler.class);

    private static final String ACTION_REPORT_RESULT = "action_report_result";
    private static final String VALIDATION_REPORT_RESULT = "validation_report_result";

    private final ChouetteClient chouetteClient;
    private final ChouetteJobs chouetteJobs;
    private final MardukInternalBlobStoreService internalBlobStore;
    private final JobEventPublisher jobEvents;
    private final MardukPubSubPublisher publisher;
    private final RetryPolicy retryPolicy;
    private final String nisabaExchangeContainer;

    public ChouetteImportResultHandler(
            ChouetteClient chouetteClient,
            ChouetteJobs chouetteJobs,
            MardukInternalBlobStoreService internalBlobStore,
            JobEventPublisher jobEvents,
            MardukPubSubPublisher publisher,
            RetryPolicy retryPolicy,
            @Value("${blobstore.gcs.nisaba.exchange.container.name}") String nisabaExchangeContainer) {
        this.chouetteClient = chouetteClient;
        this.chouetteJobs = chouetteJobs;
        this.internalBlobStore = internalBlobStore;
        this.jobEvents = jobEvents;
        this.publisher = publisher;
        this.retryPolicy = retryPolicy;
        this.nisabaExchangeContainer = nisabaExchangeContainer;
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
            copyOriginalDataset(message);
            triggerValidationUnlessMoreImportsAreQueued(message);
            report(message, JobEvent.State.OK);
            return;
        }
        if ("OK".equals(actionReport) && "NOK".equals(validationReport)) {
            LOGGER.info("Import ok but validation failed");
        } else if ("NOK".equals(actionReport)) {
            LOGGER.warn("Import not ok");
        } else {
            LOGGER.error("Something went wrong on import");
        }
        report(message, JobEvent.State.FAILED);
    }

    /**
     * Keeps the imported archive under a key naming the dataspace and the moment Chouette recorded it, so
     * nisaba can find the dataset an import produced.
     *
     * <p>The generation is copied too: a later upload under the same handle must not silently change what was
     * archived for this import.
     */
    private void copyOriginalDataset(MardukMessage message) {
        String referential = message.getHeader(CHOUETTE_REFERENTIAL, String.class);
        String lastUpdate = chouetteClient.getString(
                "/chouette_iev/referentials/" + referential + "/last_update_date");
        String importKey = referential + "_" + lastUpdate.replace(':', '_');
        retryPolicy.run("Copying the imported dataset for nisaba", () ->
                internalBlobStore.copyVersionedBlobToAnotherBucket(
                        message.getHeader(FILE_HANDLE, String.class),
                        message.getHeader(FILE_VERSION, Long.class),
                        nisabaExchangeContainer,
                        "imported/" + referential + "/" + importKey + ".zip"));
    }

    /**
     * Only the last import of a batch triggers validation.
     *
     * <p>Several files can be imported into one dataspace at once, and validating after each of them would
     * both waste a Chouette job and validate a half-imported dataspace.
     */
    private void triggerValidationUnlessMoreImportsAreQueued(MardukMessage message) {
        if (chouetteJobs.hasQueuedImports(message.getHeader(CHOUETTE_REFERENTIAL, String.class))) {
            LOGGER.info("Import ok, skipping next step as there are more import jobs active");
            return;
        }
        LOGGER.info("Import ok, triggering validation");
        MardukMessage validation = message.copy()
                .setHeader(CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL,
                        JobEvent.TimetableAction.VALIDATION_LEVEL_1.name());
        validation.setBody("");
        publisher.publish(MardukQueues.CHOUETTE_VALIDATION_QUEUE, validation);
    }

    private void report(MardukMessage message, JobEvent.State state) {
        jobEvents.reportProviderJob(message, builder -> builder
                .timetableAction(JobEvent.TimetableAction.IMPORT).state(state));
        message.removeHeader(Constants.CHOUETTE_JOB_ID);
    }
}

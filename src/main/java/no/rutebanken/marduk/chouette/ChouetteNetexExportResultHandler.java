package no.rutebanken.marduk.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubPublisher;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.validation.AntuValidation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_NETEX_EXPORT;
import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_NETEX_EXPORT_BEFORE_VALIDATION;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_EXPORT_NETEX_POSTVALIDATION;

/**
 * What happens once Chouette has finished exporting a dataspace as NeTEx.
 *
 * <p>The export is downloaded, stored, and handed to antu for post-validation. Whether it is stored where the
 * rest of the pipeline reads it, and whether the flexible-lines merge and the blocks export follow, is decided
 * by {@code chouette.enablePostValidation}: with it off the archive is parked under a separate prefix and
 * nothing downstream is triggered. Post-validation itself is requested either way, which is what the routes
 * did despite the flag's name.
 *
 * <p>Replaces {@code direct:processNetexExportResult} and the three routes it called.
 */
@Component
public class ChouetteNetexExportResultHandler implements ChouetteJobResultHandler {

    static final String DESTINATION = "direct:processNetexExportResult";

    private static final Logger LOGGER = LoggerFactory.getLogger(ChouetteNetexExportResultHandler.class);

    private final ChouetteNetexExport export;
    private final AntuValidation antuValidation;
    private final JobEventPublisher jobEvents;
    private final MardukPubSubPublisher publisher;
    private final boolean enablePostValidation;

    public ChouetteNetexExportResultHandler(
            ChouetteNetexExport export,
            AntuValidation antuValidation,
            JobEventPublisher jobEvents,
            MardukPubSubPublisher publisher,
            @Value("${chouette.enablePostValidation:true}") boolean enablePostValidation) {
        this.export = export;
        this.antuValidation = antuValidation;
        this.jobEvents = jobEvents;
        this.publisher = publisher;
        this.enablePostValidation = enablePostValidation;
    }

    @Override
    public String destination() {
        return DESTINATION;
    }

    @Override
    public void handle(MardukMessage message) {
        String actionReport = message.getHeader("action_report_result", String.class);
        message.setBody("");

        if ("OK".equals(actionReport)) {
            storeAndPostValidate(message);
            report(message, JobEvent.State.OK);
        } else {
            if (!"NOK".equals(actionReport)) {
                LOGGER.error("System error during Netex export");
            } else {
                export.recordAnEmptyExport(message, JobEvent.JOB_ERROR_NETEX_EXPORT_EMPTY);
                LOGGER.warn("Netex export failed with error code {}",
                        message.getHeader(Constants.JOB_ERROR_CODE));
            }
            report(message, JobEvent.State.FAILED);
        }
        message.removeHeader(Constants.CHOUETTE_JOB_ID);
    }

    private void storeAndPostValidate(MardukMessage message) {
        LOGGER.info("NeTEx export successful. Downloading export data");
        String referential = message.getHeader(CHOUETTE_REFERENTIAL, String.class);
        String prefix = enablePostValidation
                ? BLOBSTORE_PATH_NETEX_EXPORT
                : BLOBSTORE_PATH_NETEX_EXPORT_BEFORE_VALIDATION;
        export.store(message, prefix + referential + "-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME);

        if (enablePostValidation) {
            publisher.publish(MardukQueues.CHOUETTE_MERGE_WITH_FLEXIBLE_LINES_QUEUE, message.copy());
            publisher.publish(MardukQueues.CHOUETTE_EXPORT_NETEX_BLOCKS_QUEUE, message.copy());
        }
        antuValidation.requestPostValidation(message, VALIDATION_STAGE_EXPORT_NETEX_POSTVALIDATION);
    }

    private void report(MardukMessage message, JobEvent.State state) {
        jobEvents.reportProviderJob(message, builder -> builder
                .timetableAction(JobEvent.TimetableAction.EXPORT_NETEX).state(state));
    }
}

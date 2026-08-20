package no.rutebanken.marduk.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.validation.AntuValidation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_NETEX_BLOCKS_EXPORT;
import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_NETEX_BLOCKS_EXPORT_BEFORE_VALIDATION;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_EXPORT_NETEX_BLOCKS_POSTVALIDATION;

/**
 * What happens once Chouette has finished the blocks export.
 *
 * <p>Same shape as the plain NeTEx export, minus the downstream triggers: a blocks archive is not merged with
 * flexible lines and does not start another export. It is stored and handed to antu.
 *
 * <p>Replaces {@code direct:processNetexBlocksExportResult} and the three routes it called.
 */
@Component
public class ChouetteNetexBlocksExportResultHandler implements ChouetteJobResultHandler {

    static final String DESTINATION = "direct:processNetexBlocksExportResult";

    private static final Logger LOGGER = LoggerFactory.getLogger(ChouetteNetexBlocksExportResultHandler.class);

    private final ChouetteNetexExport export;
    private final AntuValidation antuValidation;
    private final JobEventPublisher jobEvents;
    private final boolean enablePostValidation;

    public ChouetteNetexBlocksExportResultHandler(
            ChouetteNetexExport export,
            AntuValidation antuValidation,
            JobEventPublisher jobEvents,
            @Value("${chouette.enablePostValidation:true}") boolean enablePostValidation) {
        this.export = export;
        this.antuValidation = antuValidation;
        this.jobEvents = jobEvents;
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
            LOGGER.info("NeTEx Blocks export successful. Downloading export data");
            String prefix = enablePostValidation
                    ? BLOBSTORE_PATH_NETEX_BLOCKS_EXPORT
                    : BLOBSTORE_PATH_NETEX_BLOCKS_EXPORT_BEFORE_VALIDATION;
            export.store(message, prefix + message.getHeader(CHOUETTE_REFERENTIAL, String.class)
                    + "-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME);
            antuValidation.requestPostValidation(message, VALIDATION_STAGE_EXPORT_NETEX_BLOCKS_POSTVALIDATION);
            report(message, JobEvent.State.OK);
        } else {
            if (!"NOK".equals(actionReport)) {
                LOGGER.error("Something went wrong on Netex blocks export");
            } else {
                export.recordAnEmptyExport(message, JobEvent.JOB_ERROR_NETEX_EXPORT_EMPTY);
                LOGGER.warn("Netex blocks export failed with error code {}",
                        message.getHeader(Constants.JOB_ERROR_CODE));
            }
            report(message, JobEvent.State.FAILED);
        }
        message.removeHeader(Constants.CHOUETTE_JOB_ID);
    }

    private void report(MardukMessage message, JobEvent.State state) {
        jobEvents.reportProviderJob(message, builder -> builder
                .timetableAction(JobEvent.TimetableAction.EXPORT_NETEX_BLOCKS).state(state));
    }
}

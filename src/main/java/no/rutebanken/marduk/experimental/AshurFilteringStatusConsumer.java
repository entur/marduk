package no.rutebanken.marduk.experimental;

import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubConsumer;
import no.rutebanken.marduk.pubsub.MardukPubSubPublisher;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.routes.experimental.AshurFilteringReportValidator;
import no.rutebanken.marduk.routes.experimental.ExperimentalImportHelpers;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import no.rutebanken.marduk.validation.NetexValidationProfiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILTERED_NETEX_FILE_PATH_HEADER;
import static no.rutebanken.marduk.Constants.FILTERING_ERROR_CODE_HEADER;
import static no.rutebanken.marduk.Constants.FILTERING_PROFILE_HEADER;
import static no.rutebanken.marduk.Constants.FILTERING_PROFILE_INCLUDE_BLOCKS_AND_RESTRICTED_JOURNEYS;
import static no.rutebanken.marduk.Constants.FILTERING_PROFILE_STANDARD_IMPORT;
import static no.rutebanken.marduk.Constants.FILTER_NETEX_FILE_STATUS_FAILED;
import static no.rutebanken.marduk.Constants.FILTER_NETEX_FILE_STATUS_HEADER;
import static no.rutebanken.marduk.Constants.FILTER_NETEX_FILE_STATUS_STARTED;
import static no.rutebanken.marduk.Constants.FILTER_NETEX_FILE_STATUS_SUCCEEDED;
import static no.rutebanken.marduk.Constants.SECURITY_ERROR_CODE_FILTERING_PROFILE_MISMATCH;
import static no.rutebanken.marduk.Constants.SOURCE_CONTAINER;
import static no.rutebanken.marduk.Constants.TARGET_CONTAINER;
import static no.rutebanken.marduk.Constants.TARGET_FILE_HANDLE;
import static no.rutebanken.marduk.Constants.VALIDATION_CLIENT_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_CLIENT_MARDUK;
import static no.rutebanken.marduk.Constants.VALIDATION_CORRELATION_ID_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_DATASET_FILE_HANDLE_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_PROFILE_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_EXPORT_NETEX_BLOCKS_POSTVALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_EXPORT_NETEX_POSTVALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_HEADER;

/**
 * Handles Ashur's verdict on a filtering job, and asks antu to post-validate what came back.
 *
 * <p>Ashur runs two filters for marduk and both report here, so the profile attribute decides which job the
 * status belongs to and which path the filtered dataset takes: the standard import filter feeds the ordinary
 * NeTEx post-validation, the blocks filter feeds the blocks post-validation.
 *
 * <p>A report that does not name the profile marduk asked for stops the flow. That is a security control,
 * not a sanity check - see {@link AshurFilteringReportValidator}.
 *
 * <p>Replaces {@code AshurFilteringStatusRouteBuilder} and its eight routes.
 */
@Component
public class AshurFilteringStatusConsumer extends MardukPubSubConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AshurFilteringStatusConsumer.class);

    private final ExperimentalImportHelpers helpers;
    private final AshurFilteringReportValidator reportValidator;
    private final NetexValidationProfiles netexValidationProfiles;
    private final MardukInternalBlobStoreService internalBlobStore;
    private final MardukPubSubPublisher publisher;
    private final JobEventPublisher jobEvents;
    private final String ashurExchangeContainer;
    private final String antuExchangeContainer;

    public AshurFilteringStatusConsumer(
            ExperimentalImportHelpers helpers,
            AshurFilteringReportValidator reportValidator,
            NetexValidationProfiles netexValidationProfiles,
            MardukInternalBlobStoreService internalBlobStore,
            MardukPubSubPublisher publisher,
            JobEventPublisher jobEvents,
            @Value("${blobstore.gcs.ashur.exchange.container.name}") String ashurExchangeContainer,
            @Value("${blobstore.gcs.antu.exchange.container.name}") String antuExchangeContainer) {
        this.helpers = helpers;
        this.reportValidator = reportValidator;
        this.netexValidationProfiles = netexValidationProfiles;
        this.internalBlobStore = internalBlobStore;
        this.publisher = publisher;
        this.jobEvents = jobEvents;
        this.ashurExchangeContainer = ashurExchangeContainer;
        this.antuExchangeContainer = antuExchangeContainer;
    }

    @Override
    protected String destination() {
        return MardukQueues.FILTER_NETEX_FILE_STATUS_QUEUE;
    }

    @Override
    protected void handle(MardukMessage message) {
        String profile = message.getHeader(FILTERING_PROFILE_HEADER, String.class);
        switch (profile) {
            case FILTERING_PROFILE_STANDARD_IMPORT -> standardImportStatus(message);
            case FILTERING_PROFILE_INCLUDE_BLOCKS_AND_RESTRICTED_JOURNEYS -> blocksExportStatus(message);
            case null, default -> LOGGER.error(
                    "Received notification with unknown Ashur filtering profile: {}", profile);
        }
    }

    private void standardImportStatus(MardukMessage message) {
        String status = message.getHeader(FILTER_NETEX_FILE_STATUS_HEADER, String.class);
        switch (status) {
            case FILTER_NETEX_FILE_STATUS_STARTED -> {
                LOGGER.info("Received notification that Ashur filtering has started for standard import.");
                report(message, JobEvent.TimetableAction.FILTERING, JobEvent.State.STARTED);
            }
            case FILTER_NETEX_FILE_STATUS_SUCCEEDED -> {
                LOGGER.info("Received notification that Ashur filtering has succeeded for standard import. File location: {}",
                        message.getHeader(FILTERED_NETEX_FILE_PATH_HEADER, String.class));
                AshurFilteringReportValidator.Verdict verdict = reportValidator.validateStandardImportReport(
                        message.getBody(String.class), message.getHeader(CORRELATION_ID, String.class));
                if (!verdict.valid()) {
                    LOGGER.error("SECURITY: Ashur filtering report validation failed for standard import: {}",
                            verdict.reason());
                    reportFailed(message, JobEvent.TimetableAction.FILTERING,
                            SECURITY_ERROR_CODE_FILTERING_PROFILE_MISMATCH);
                    return;
                }
                postValidate(message, helpers.pathToNetexWithoutBlocksProducedByAshur(message),
                        VALIDATION_STAGE_EXPORT_NETEX_POSTVALIDATION,
                        JobEvent.TimetableAction.EXPORT_NETEX_POSTVALIDATION);
                report(message, JobEvent.TimetableAction.FILTERING, JobEvent.State.OK);
            }
            case FILTER_NETEX_FILE_STATUS_FAILED -> {
                LOGGER.info("Received notification that Ashur filtering has failed for standard import.");
                reportFailed(message, JobEvent.TimetableAction.FILTERING,
                        message.getHeader(FILTERING_ERROR_CODE_HEADER, String.class));
            }
            case null, default -> LOGGER.error(
                    "Received notification with unknown Ashur filtering status for standard import: {}", status);
        }
    }

    private void blocksExportStatus(MardukMessage message) {
        String status = message.getHeader(FILTER_NETEX_FILE_STATUS_HEADER, String.class);
        switch (status) {
            case FILTER_NETEX_FILE_STATUS_STARTED -> {
                LOGGER.info("Received notification that Ashur filtering has started for block export.");
                report(message, JobEvent.TimetableAction.EXPORT_NETEX_BLOCKS, JobEvent.State.STARTED);
            }
            case FILTER_NETEX_FILE_STATUS_SUCCEEDED -> {
                LOGGER.info("Received notification that Ashur filtering has succeeded for block export. File location: {}",
                        message.getHeader(FILTERED_NETEX_FILE_PATH_HEADER, String.class));
                AshurFilteringReportValidator.Verdict verdict = reportValidator.validateBlocksExportReport(
                        message.getBody(String.class), message.getHeader(CORRELATION_ID, String.class));
                if (!verdict.valid()) {
                    LOGGER.error("SECURITY: Ashur filtering report validation failed for blocks export: {}",
                            verdict.reason());
                    reportFailed(message, JobEvent.TimetableAction.EXPORT_NETEX_BLOCKS,
                            SECURITY_ERROR_CODE_FILTERING_PROFILE_MISMATCH);
                    return;
                }
                postValidate(message, helpers.pathToNetexWithBlocksProducedByAshur(message),
                        VALIDATION_STAGE_EXPORT_NETEX_BLOCKS_POSTVALIDATION,
                        JobEvent.TimetableAction.EXPORT_NETEX_BLOCKS_POSTVALIDATION);
                report(message, JobEvent.TimetableAction.EXPORT_NETEX_BLOCKS, JobEvent.State.OK);
            }
            case FILTER_NETEX_FILE_STATUS_FAILED -> {
                LOGGER.info("Received notification that Ashur filtering has failed for block export.");
                // Unlike the standard import, the blocks export does not relay Ashur's error code.
                report(message, JobEvent.TimetableAction.EXPORT_NETEX_BLOCKS, JobEvent.State.FAILED);
            }
            case null, default -> LOGGER.error(
                    "Received notification with unknown Ashur filtering status for block export: {}", status);
        }
    }

    /**
     * Moves the filtered dataset out of Ashur's bucket and asks antu to validate it.
     *
     * @param filtered the path the dataset gets in marduk's internal bucket, which is also the path antu
     *                 reads it from and the handle it reports back under
     */
    private void postValidate(MardukMessage message, String filtered, String stage, JobEvent.TimetableAction action) {
        String fromAshur = message.getHeader(FILTERED_NETEX_FILE_PATH_HEADER, String.class);
        message.setHeader(FILE_HANDLE, fromAshur);
        message.setHeader(TARGET_FILE_HANDLE, filtered);
        message.setHeader(SOURCE_CONTAINER, ashurExchangeContainer);
        internalBlobStore.copyBlobFromAnotherBucket(ashurExchangeContainer, fromAshur, filtered);

        message.setHeader(FILE_HANDLE, filtered);
        message.setHeader(TARGET_CONTAINER, antuExchangeContainer);
        message.setHeader(TARGET_FILE_HANDLE, filtered);
        internalBlobStore.copyBlobToAnotherBucket(filtered, antuExchangeContainer, filtered);

        LOGGER.info("Triggering post-validation of filtered dataset in Antu.");
        message.setHeader(VALIDATION_STAGE_HEADER, stage);
        message.setHeader(VALIDATION_CLIENT_HEADER, VALIDATION_CLIENT_MARDUK);
        message.setHeader(VALIDATION_DATASET_FILE_HANDLE_HEADER, filtered);
        message.setHeader(VALIDATION_CORRELATION_ID_HEADER, message.getHeader(CORRELATION_ID, String.class));
        // The route set the plain timetable profile first and then overwrote it with the codespace's own;
        // only the second ever took effect.
        message.setHeader(VALIDATION_PROFILE_HEADER,
                netexValidationProfiles.profileFor(message.getHeader(DATASET_REFERENTIAL, String.class)));
        publisher.publish(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE, message);
        jobEvents.reportProviderJob(message, builder -> builder
                .timetableAction(action).state(JobEvent.State.PENDING).jobId(null));
    }

    private void report(MardukMessage message, JobEvent.TimetableAction action, JobEvent.State state) {
        jobEvents.reportProviderJob(message, builder -> builder.timetableAction(action).state(state));
    }

    /**
     * A failure overrides the error code the builder reads off the message, so that a stale
     * {@code RutebankenJobErrorCode} cannot be reported as the reason this step failed.
     */
    private void reportFailed(MardukMessage message, JobEvent.TimetableAction action, String errorCode) {
        jobEvents.reportProviderJob(message, builder -> builder
                .timetableAction(action).state(JobEvent.State.FAILED).errorCode(errorCode));
    }
}

package no.rutebanken.marduk.antu;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.experimental.ExperimentalImportPath;
import no.rutebanken.marduk.pipeline.MardukMdc;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubConsumer;
import no.rutebanken.marduk.pubsub.MardukPubSubPublisher;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.experimental.ExperimentalImportHelpers;
import no.rutebanken.marduk.routes.file.FileType;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.ExchangeBlobStoreService;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import no.rutebanken.marduk.validation.ValidationStages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;
import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.CURRENT_AGGREGATED_NETEX_FILENAME;
import static no.rutebanken.marduk.Constants.CURRENT_FLEXIBLE_LINES_NETEX_FILENAME;
import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.FILE_TYPE;
import static no.rutebanken.marduk.Constants.IMPORT_TYPE_NETEX_FLEX;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.TARGET_CONTAINER;
import static no.rutebanken.marduk.Constants.TARGET_FILE_HANDLE;
import static no.rutebanken.marduk.Constants.VALIDATION_CORRELATION_ID_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_DATASET_FILE_HANDLE_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_IMPORT_TYPE;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_EXPORT_MERGED_POSTVALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_EXPORT_NETEX_BLOCKS_POSTVALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_EXPORT_NETEX_POSTVALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_FLEX_POSTVALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_NIGHTLY_VALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_PREVALIDATION;

/**
 * Handles antu's verdict on a dataset, at every stage of the pipeline that asks antu to validate one.
 *
 * <p>Two attributes decide everything: the status in the message body says whether the validation started,
 * passed, failed or timed out, and {@code EnturValidationStage} says which of the six validations it was.
 * The stage decides both which job the status belongs to - see {@link ValidationStages#actionFor} - and, for a
 * validation that passed, what happens next.
 *
 * <p>Replaces {@code AntuNetexValidationStatusRouteBuilder} and its four routes.
 */
@Component
public class AntuValidationStatusConsumer extends MardukPubSubConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AntuValidationStatusConsumer.class);

    /** Antu sends the verdict as the message body, not an attribute. Pinned by {@code WireContractTest}. */
    static final String STATUS_VALIDATION_STARTED = "started";
    static final String STATUS_VALIDATION_OK = "ok";
    static final String STATUS_VALIDATION_FAILED = "failed";
    /** Antu could not complete the validation, as opposed to the dataset being invalid. */
    static final String STATUS_VALIDATION_TIMEOUT = "timeout";

    private final ProviderRepository providerRepository;
    private final ExperimentalImportHelpers experimentalImportHelpers;
    private final ExperimentalImportPath experimentalImportPath;
    private final PrevalidatedDataset prevalidatedDataset;
    private final MardukInternalBlobStoreService internalBlobStore;
    private final ExchangeBlobStoreService exchangeBlobStore;
    private final MardukPubSubPublisher publisher;
    private final JobEventPublisher jobEvents;
    private final boolean enablePreValidation;
    private final boolean enablePostValidation;
    private final String exchangeContainer;
    private final String publicContainer;

    public AntuValidationStatusConsumer(
            ProviderRepository providerRepository,
            ExperimentalImportHelpers experimentalImportHelpers,
            ExperimentalImportPath experimentalImportPath,
            PrevalidatedDataset prevalidatedDataset,
            MardukInternalBlobStoreService internalBlobStore,
            ExchangeBlobStoreService exchangeBlobStore,
            MardukPubSubPublisher publisher,
            JobEventPublisher jobEvents,
            @Value("${chouette.enablePreValidation:true}") boolean enablePreValidation,
            @Value("${chouette.enablePostValidation:true}") boolean enablePostValidation,
            @Value("${blobstore.gcs.exchange.container.name}") String exchangeContainer,
            @Value("${blobstore.gcs.container.name}") String publicContainer) {
        this.providerRepository = providerRepository;
        this.experimentalImportHelpers = experimentalImportHelpers;
        this.experimentalImportPath = experimentalImportPath;
        this.prevalidatedDataset = prevalidatedDataset;
        this.internalBlobStore = internalBlobStore;
        this.exchangeBlobStore = exchangeBlobStore;
        this.publisher = publisher;
        this.jobEvents = jobEvents;
        this.enablePreValidation = enablePreValidation;
        this.enablePostValidation = enablePostValidation;
        this.exchangeContainer = exchangeContainer;
        this.publicContainer = publicContainer;
    }

    @Override
    protected String destination() {
        return MardukQueues.ANTU_NETEX_VALIDATION_STATUS_QUEUE;
    }

    @Override
    protected void handle(MardukMessage message) {
        String fileHandle = required(message, VALIDATION_DATASET_FILE_HANDLE_HEADER);
        String referential = message.getHeader(DATASET_REFERENTIAL, String.class);

        message.setHeader(CORRELATION_ID, required(message, VALIDATION_CORRELATION_ID_HEADER));
        message.setHeader(FILE_HANDLE, fileHandle);
        message.setHeader(FILE_TYPE, FileType.NETEXPROFILE);
        message.setHeader(CHOUETTE_REFERENTIAL, referential);
        message.setHeader(PROVIDER_ID, providerRepository.getProviderId(referential));
        MardukMdc.set(message);

        String status = message.getBody(String.class);
        LOGGER.info("Received Antu NeTEx validation status update for referential {}, status {}", referential, status);
        message.setHeader(FILE_NAME, fileNameOf(fileHandle));

        switch (status) {
            case STATUS_VALIDATION_STARTED -> validationStarted(message);
            case STATUS_VALIDATION_OK -> validationComplete(message);
            case STATUS_VALIDATION_FAILED -> validationFailed(message, JobEvent.State.FAILED);
            case STATUS_VALIDATION_TIMEOUT -> {
                message.setHeader(Constants.JOB_ERROR_CODE, JobEvent.JOB_ERROR_VALIDATION_INCOMPLETE);
                validationFailed(message, JobEvent.State.TIMEOUT);
            }
            // Discarded, not nacked: a status no version of marduk understands would be redelivered for ever.
            case null, default -> LOGGER.error(
                    "Unknown Antu validation status {} for referential {}. Discarding.", status, referential);
        }
    }

    private void validationStarted(MardukMessage message) {
        LOGGER.info("Antu NeTEx validation started for referential {}",
                message.getHeader(DATASET_REFERENTIAL, String.class));
        JobEvent.TimetableAction action = knownActionFor(message);
        if (action == null) {
            return;
        }
        // jobId(null) drops the antu report id the builder picks up off the message: a validation that has
        // only started has no report to link to yet.
        jobEvents.reportProviderJob(message, builder -> builder
                .timetableAction(action).state(JobEvent.State.STARTED).jobId(null));
    }

    private void validationFailed(MardukMessage message, JobEvent.State state) {
        JobEvent.TimetableAction action = knownActionFor(message);
        if (action == null) {
            return;
        }
        jobEvents.reportProviderJob(message, builder -> builder.timetableAction(action).state(state));
    }

    private void validationComplete(MardukMessage message) {
        LOGGER.info("Antu NeTEx validation complete for referential {}",
                message.getHeader(DATASET_REFERENTIAL, String.class));
        String stage = message.getHeader(VALIDATION_STAGE_HEADER, String.class);
        switch (stage) {
            case VALIDATION_STAGE_PREVALIDATION -> {
                if (experimentalImportHelpers.shouldRunExperimentalImport(message)) {
                    prevalidationCompleteForExperimentalImport(message);
                } else {
                    prevalidationCompleteForChouetteImport(message);
                }
            }
            case VALIDATION_STAGE_EXPORT_NETEX_POSTVALIDATION -> {
                if (experimentalImportHelpers.shouldRunExperimentalImport(message)) {
                    netexPostValidationCompleteForExperimentalImport(message);
                } else {
                    netexPostValidationCompleteForChouetteImport(message);
                }
            }
            case VALIDATION_STAGE_EXPORT_NETEX_BLOCKS_POSTVALIDATION -> netexBlocksPostValidationComplete(message);
            case VALIDATION_STAGE_FLEX_POSTVALIDATION -> flexPostValidationComplete(message);
            case VALIDATION_STAGE_EXPORT_MERGED_POSTVALIDATION -> mergedPostValidationComplete(message);
            case VALIDATION_STAGE_NIGHTLY_VALIDATION -> {
                if (experimentalImportHelpers.shouldRunExperimentalImport(message)) {
                    nightlyValidationCompleteForExperimentalImport(message);
                } else {
                    nightlyValidationCompleteForChouetteImport(message);
                }
            }
            case null, default -> LOGGER.error("Unknown validation stage {}", stage);
        }
    }

    /**
     * The pre-validation passed on a codespace that runs the experimental import.
     *
     * <p>The completed pre-validation is reported before the enrichment and filtering step, because that
     * step is where the {@code rb_} prefix goes onto the referential headers and the links to antu's
     * pre-validation reports are built from the unprefixed value.
     */
    private void prevalidationCompleteForExperimentalImport(MardukMessage message) {
        prevalidatedDataset.stampCreatedTimestamp(message);
        prevalidatedDataset.archiveToNisaba(message);
        prevalidatedDataset.recordAsLastPrevalidated(message);
        LOGGER.info("Experimental import is enabled for codespace, triggering enrichment and filtering after pre-validation");
        reportComplete(message);
        experimentalImportPath.enrichThenFilter(message);
    }

    private void prevalidationCompleteForChouetteImport(MardukMessage message) {
        prevalidatedDataset.recordAsLastPrevalidated(message);
        if (enablePreValidation) {
            // Chouette validates during the import itself, so the import is triggered elsewhere and nothing
            // is reported here. The Camel route did send its message body to nabu at this point, but the
            // body was the empty string the blob store route left behind, which nabu cannot read as a job
            // event; nothing is published instead.
            return;
        }
        LOGGER.info("Posting {} {} and {} {} on chouette import queue.",
                FILE_HANDLE, message.getHeader(FILE_HANDLE, String.class),
                FILE_TYPE, message.getHeader(FILE_TYPE, String.class));
        // The blob store route left the body empty after writing the metadata file, and the import trigger
        // carried that rather than antu's status.
        message.setBody("");
        publisher.publish(MardukQueues.CHOUETTE_IMPORT_QUEUE, message);
        reportComplete(message);
    }

    private void netexPostValidationCompleteForExperimentalImport(MardukMessage message) {
        message.setHeader(FILE_HANDLE, experimentalImportHelpers.pathToNetexWithoutBlocksProducedByAshur(message));
        copyInBucket(message, experimentalImportHelpers.pathToNetexFromAshurToMergeWithFlex(message));
        // Also publish the without-blocks Ashur output at a stable per-referential path so a later
        // cross-flow merge (e.g. after FLEX post-validation, which carries the FLEX import's correlation
        // id) can locate the latest ordinary NeTEx for this codespace.
        copyInBucket(message, experimentalImportHelpers.pathToLatestNetexWithoutBlocksFromAshur(message));
        publisher.publish(MardukQueues.CHOUETTE_MERGE_WITH_FLEXIBLE_LINES_QUEUE, message);

        if (providerRepository.getProvider(message.getHeader(PROVIDER_ID, Long.class))
                .getChouetteInfo().isEnableBlocksExport()) {
            LOGGER.info("Starting block export with experimental import");
            publisher.publish(MardukQueues.EXPORT_NETEX_BLOCKS_QUEUE, message);
        } else {
            LOGGER.info("Skipping export of NetEx blocks to Ashur after post-validation because provider has blocks export disabled");
        }
        reportComplete(message);
    }

    private void netexPostValidationCompleteForChouetteImport(MardukMessage message) {
        if (!enablePostValidation) {
            copyInBucket(message, experimentalImportHelpers.pathToNetexExportFromChouetteToMergeWithFlex(message));
            // Mirror to the shared per-referential fallback path so the experimental merge route (which
            // consults this path when its correlation-keyed Ashur primary read is empty) can find a recent
            // ordinary export even on a codespace where the experimental pipeline has not yet produced one.
            copyInBucket(message, experimentalImportHelpers.pathToLatestNetexWithoutBlocksFromAshur(message));
            publisher.publish(MardukQueues.CHOUETTE_MERGE_WITH_FLEXIBLE_LINES_QUEUE, message);
            publisher.publish(MardukQueues.CHOUETTE_EXPORT_NETEX_BLOCKS_QUEUE, message);
        }
        reportComplete(message);
    }

    private void netexBlocksPostValidationComplete(MardukMessage message) {
        if (!enablePostValidation) {
            copyInBucket(message, Constants.BLOBSTORE_PATH_NETEX_BLOCKS_EXPORT
                    + message.getHeader(CHOUETTE_REFERENTIAL, String.class) + "-" + CURRENT_AGGREGATED_NETEX_FILENAME);
        }
        reportComplete(message);
    }

    private void flexPostValidationComplete(MardukMessage message) {
        String referential = message.getHeader(CHOUETTE_REFERENTIAL, String.class);
        boolean uploadedFromOperatorPortal =
                IMPORT_TYPE_NETEX_FLEX.equals(message.getHeader(VALIDATION_IMPORT_TYPE, String.class));
        if (uploadedFromOperatorPortal) {
            message.setHeader(CHOUETTE_REFERENTIAL, "rb_" + message.getHeader(DATASET_REFERENTIAL, String.class));
        }
        String target = BLOBSTORE_PATH_OUTBOUND + "netex/"
                + message.getHeader(CHOUETTE_REFERENTIAL, String.class) + "-" + CURRENT_FLEXIBLE_LINES_NETEX_FILENAME;
        message.setHeader(TARGET_FILE_HANDLE, target);
        String dataset = message.getHeader(FILE_HANDLE, String.class);
        if (uploadedFromOperatorPortal) {
            // A dataset uploaded from the operator portal was stored in the internal bucket and has to be
            // copied to the exchange bucket.
            message.setHeader(TARGET_CONTAINER, exchangeContainer);
            internalBlobStore.copyBlobToAnotherBucket(dataset, exchangeContainer, target);
        } else {
            // Everything else comes from uttu and is already in the inbound folder of the exchange bucket,
            // so it only moves to the outbound folder of the same bucket.
            exchangeBlobStore.copyBlobInBucket(dataset, target);
        }
        publisher.publish(MardukQueues.CHOUETTE_MERGE_WITH_FLEXIBLE_LINES_QUEUE, message);
        reportComplete(message, referential);
    }

    private void mergedPostValidationComplete(MardukMessage message) {
        String referential = message.getHeader(CHOUETTE_REFERENTIAL, String.class);
        if (IMPORT_TYPE_NETEX_FLEX.equals(message.getHeader(VALIDATION_IMPORT_TYPE, String.class))) {
            message.setHeader(CHOUETTE_REFERENTIAL, "rb_" + message.getHeader(CHOUETTE_REFERENTIAL, String.class));
        }
        String target = BLOBSTORE_PATH_OUTBOUND + "netex/"
                + message.getHeader(CHOUETTE_REFERENTIAL, String.class) + "-" + CURRENT_AGGREGATED_NETEX_FILENAME;
        message.setHeader(TARGET_FILE_HANDLE, target);
        message.setHeader(TARGET_CONTAINER, publicContainer);
        internalBlobStore.copyBlobToAnotherBucket(
                message.getHeader(FILE_HANDLE, String.class), publicContainer, target);
        publisher.publish(MardukQueues.PUBLISH_MERGED_NETEX_QUEUE, message);
        reportComplete(message, referential);
    }

    /** Reports before the enrichment and filtering step for the same reason the pre-validation path does. */
    private void nightlyValidationCompleteForExperimentalImport(MardukMessage message) {
        LOGGER.info("Nightly validation: Experimental import is enabled for codespace, triggering enrichment and filtering after pre-validation");
        reportComplete(message);
        experimentalImportPath.enrichThenFilter(message);
    }

    private void nightlyValidationCompleteForChouetteImport(MardukMessage message) {
        message.setHeader(CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL,
                JobEvent.TimetableAction.VALIDATION_LEVEL_1.name());
        publisher.publish(MardukQueues.CHOUETTE_VALIDATION_QUEUE, message);
        reportComplete(message);
    }

    /**
     * Reports the stage's job as OK, last in the branch: a report sent before the copies and the downstream
     * trigger would tell nabu the step succeeded even though the redelivery has yet to do the work.
     *
     * <p>Reached only from a branch whose stage {@link ValidationStages} knows.
     */
    private void reportComplete(MardukMessage message) {
        reportComplete(message, message.getHeader(CHOUETTE_REFERENTIAL, String.class));
    }

    /**
     * @param referential the referential to report against, for the two branches that prefix
     *                    {@code CHOUETTE_REFERENTIAL} with {@code rb_} before the report goes out
     */
    private void reportComplete(MardukMessage message, String referential) {
        JobEvent.TimetableAction action = ValidationStages.actionFor(message.getHeader(VALIDATION_STAGE_HEADER, String.class));
        jobEvents.reportProviderJob(message, builder -> {
            builder.timetableAction(action).state(JobEvent.State.OK);
            if (referential != null) {
                builder.referential(referential);
            }
        });
    }

    private void copyInBucket(MardukMessage message, String target) {
        message.setHeader(TARGET_FILE_HANDLE, target);
        internalBlobStore.copyBlobInBucket(message.getHeader(FILE_HANDLE, String.class), target);
    }

    private static JobEvent.TimetableAction knownActionFor(MardukMessage message) {
        String stage = message.getHeader(VALIDATION_STAGE_HEADER, String.class);
        JobEvent.TimetableAction action = ValidationStages.actionFor(stage);
        if (action == null) {
            LOGGER.error("Unknown validation stage {}", stage);
        }
        return action;
    }

    /**
     * The Camel route validated these two attributes, which failed the exchange and left PubSub to redeliver.
     * Kept: a status message without them cannot be matched to a job at all.
     */
    private static String required(MardukMessage message, String attribute) {
        String value = message.getHeader(attribute, String.class);
        if (value == null) {
            throw new MardukException("Antu validation status is missing the " + attribute + " attribute");
        }
        return value;
    }

    private static String fileNameOf(String filePath) {
        return filePath.substring(filePath.lastIndexOf('/') + 1);
    }
}

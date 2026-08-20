package no.rutebanken.marduk.validation;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.pipeline.MardukMdc;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubPublisher;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.file.FileType;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_TYPE;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.VALIDATION_CLIENT_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_CLIENT_MARDUK;
import static no.rutebanken.marduk.Constants.VALIDATION_CORRELATION_ID_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_DATASET_FILE_HANDLE_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_PROFILE_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_NIGHTLY_VALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_PREVALIDATION;

/**
 * Asks antu to validate a dataset, before it is imported and again nightly.
 *
 * <p>Both paths do the same three things: copy the file into the bucket antu reads, describe the request in
 * the attributes antu matches on, and record that a validation is pending. The stage attribute is what tells
 * antu, and the status route that handles its reply, which of the two this is.
 *
 * <p>Was {@code direct:antuAntuValidation} and {@code direct:antuNetexNightlyValidation}.
 */
@Component
public class AntuValidation {

    private static final Logger LOGGER = LoggerFactory.getLogger(AntuValidation.class);

    private final MardukInternalBlobStoreService internalBlobStore;
    private final ProviderRepository providerRepository;
    private final NetexValidationProfiles netexValidationProfiles;
    private final JobEventPublisher jobEvents;
    private final MardukPubSubPublisher publisher;
    private final String antuExchangeContainer;

    public AntuValidation(
            MardukInternalBlobStoreService internalBlobStore,
            ProviderRepository providerRepository,
            NetexValidationProfiles netexValidationProfiles,
            JobEventPublisher jobEvents,
            MardukPubSubPublisher publisher,
            @Value("${blobstore.gcs.antu.exchange.container.name}") String antuExchangeContainer) {
        this.internalBlobStore = internalBlobStore;
        this.providerRepository = providerRepository;
        this.netexValidationProfiles = netexValidationProfiles;
        this.jobEvents = jobEvents;
        this.publisher = publisher;
        this.antuExchangeContainer = antuExchangeContainer;
    }

    /**
     * Pre-validation, before the import. Only NeTEx is validated; a GTFS file is left to Chouette, which is
     * the {@code filter} the route had at its head.
     */
    public void requestPreValidation(MardukMessage message) {
        if (!FileType.NETEXPROFILE.name().equals(message.getHeader(FILE_TYPE, String.class))) {
            return;
        }
        copyToValidationBucket(message);

        Provider provider = providerRepository.getProvider(message.getHeader(PROVIDER_ID, Long.class));
        String referential = provider.getChouetteInfo().getReferential();
        message.setHeader(DATASET_REFERENTIAL, referential);
        MardukMdc.setCodespaceIfMissing(referential);

        request(message, VALIDATION_STAGE_PREVALIDATION, false);
    }

    /**
     * Nightly re-validation of the last file that passed pre-validation.
     *
     * @return false if there is no such file, in which case the caller falls back to a Chouette level 1
     *         validation - the file is only kept for codespaces that have completed a pre-validation
     */
    public boolean requestNightlyValidationIfFilePresent(MardukMessage message) {
        String fileHandle = message.getHeader(FILE_HANDLE, String.class);
        if (fileHandle == null || !internalBlobStore.blobExists(fileHandle)) {
            return false;
        }
        LOGGER.info("File with file handle {} found in blob store. Triggering nightly prevalidation.", fileHandle);
        copyToValidationBucket(message);
        request(message, VALIDATION_STAGE_NIGHTLY_VALIDATION, false);
        return true;
    }

    /**
     * Post-validation of an export Chouette has just produced.
     *
     * <p>The routes set {@code VALIDATION_PROFILE_HEADER} to the plain timetable profile and then called
     * {@code direct:setNetexValidationProfile}, which overwrote it with the codespace's profile. Only the
     * second one ever took effect, so only that is done here.
     *
     * @param stage {@code VALIDATION_STAGE_EXPORT_NETEX_POSTVALIDATION} or the blocks equivalent
     */
    public void requestPostValidation(MardukMessage message, String stage) {
        copyToValidationBucket(message);
        Provider provider = providerRepository.getProvider(message.getHeader(PROVIDER_ID, Long.class));
        message.setHeader(DATASET_REFERENTIAL, provider.getChouetteInfo().getReferential());
        request(message, stage, true);
    }

    /**
     * @param clearJobId as the post-validation routes did: the message still carries the Chouette export job
     *                   that produced the file, which is not this validation's external id
     */
    private void request(MardukMessage message, String stage, boolean clearJobId) {
        message.setHeader(VALIDATION_PROFILE_HEADER,
                netexValidationProfiles.profileFor(message.getHeader(DATASET_REFERENTIAL, String.class)));
        message.setHeader(VALIDATION_STAGE_HEADER, stage);
        message.setHeader(VALIDATION_CLIENT_HEADER, VALIDATION_CLIENT_MARDUK);
        message.setHeader(VALIDATION_DATASET_FILE_HANDLE_HEADER, message.getHeader(FILE_HANDLE, String.class));
        message.setHeader(VALIDATION_CORRELATION_ID_HEADER, message.getHeader(CORRELATION_ID, String.class));
        message.setBody("");
        publisher.publish(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE, message);

        jobEvents.reportProviderJob(message, builder -> {
            builder.timetableAction(ValidationStages.actionFor(stage)).state(JobEvent.State.PENDING);
            if (clearJobId) {
                builder.jobId(null);
            }
        });
    }

    /** The same path in antu's bucket as in marduk's, which is how antu finds it from the attribute. */
    private void copyToValidationBucket(MardukMessage message) {
        String fileHandle = message.getHeader(FILE_HANDLE, String.class);
        internalBlobStore.copyBlobToAnotherBucket(fileHandle, antuExchangeContainer, fileHandle);
        message.setHeader(Constants.TARGET_CONTAINER, antuExchangeContainer);
        message.setHeader(Constants.TARGET_FILE_HANDLE, fileHandle);
    }
}

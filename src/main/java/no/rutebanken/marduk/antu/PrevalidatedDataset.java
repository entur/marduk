package no.rutebanken.marduk.antu;

import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.repository.FileNameAndDigestIdempotentRepository;
import no.rutebanken.marduk.routes.experimental.FilteringTimestampProcessor;
import no.rutebanken.marduk.routes.experimental.NisabaHeadersProcessor;
import no.rutebanken.marduk.routes.processors.PrevalidatedFileMetadataProcessor;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CURRENT_PREVALIDATED_NETEX_FILENAME;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.FILTERING_FILE_CREATED_TIMESTAMP;
import static no.rutebanken.marduk.Constants.TARGET_CONTAINER;
import static no.rutebanken.marduk.Constants.TARGET_FILE_HANDLE;

/**
 * What happens to a dataset once antu has pre-validated it: the original is archived for Nisaba, and the
 * dataset is kept as the codespace's last pre-validated file together with a metadata file naming it.
 *
 * <p>All three steps hang off the same timestamp, the instant the file was first received. It has to be
 * that one rather than now, because Nisaba and Ashur both name their output after it and would otherwise
 * disagree about which import a file belongs to.
 */
@Component
public class PrevalidatedDataset {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrevalidatedDataset.class);

    private final MardukInternalBlobStoreService internalBlobStore;
    private final FilteringTimestampProcessor filteringTimestamp;
    private final PrevalidatedFileMetadataProcessor fileMetadata;
    private final NisabaHeadersProcessor nisabaTarget;

    public PrevalidatedDataset(
            MardukInternalBlobStoreService internalBlobStore,
            FileNameAndDigestIdempotentRepository idempotentRepository,
            @Value("${blobstore.gcs.nisaba.exchange.container.name}") String nisabaExchangeContainer) {
        this.internalBlobStore = internalBlobStore;
        this.filteringTimestamp = new FilteringTimestampProcessor(idempotentRepository);
        this.fileMetadata = new PrevalidatedFileMetadataProcessor(idempotentRepository);
        this.nisabaTarget = new NisabaHeadersProcessor(nisabaExchangeContainer);
    }

    /** Records when the file was first received, for the steps downstream that name their output after it. */
    public void stampCreatedTimestamp(MardukMessage message) {
        message.setHeader(FILTERING_FILE_CREATED_TIMESTAMP,
                filteringTimestamp.createdAtFor(message.getHeader(FILE_NAME, String.class)).toString());
    }

    /** Copies the original dataset into Nisaba's bucket, under a name derived from that timestamp. */
    public void archiveToNisaba(MardukMessage message) {
        String referential = message.getHeader(CHOUETTE_REFERENTIAL, String.class);
        LOGGER.info("Uploading original dataset to Nisaba for referential {}", referential);
        NisabaHeadersProcessor.UploadTarget target = nisabaTarget.targetFor(
                referential, message.getHeader(FILTERING_FILE_CREATED_TIMESTAMP, String.class));
        message.setHeader(TARGET_FILE_HANDLE, target.fileHandle());
        message.setHeader(TARGET_CONTAINER, target.container());
        internalBlobStore.copyBlobToAnotherBucket(
                message.getHeader(FILE_HANDLE, String.class), target.container(), target.fileHandle());
    }

    /**
     * Writes the metadata file and keeps the dataset itself as the codespace's last pre-validated file.
     *
     * <p>{@code FILE_HANDLE} still points at the dataset afterwards, not at the metadata file: the nightly
     * re-validation reaches the original file through the metadata, so the handle has to survive this step.
     */
    public void recordAsLastPrevalidated(MardukMessage message) {
        String dataset = message.getHeader(FILE_HANDLE, String.class);
        String referential = message.getHeader(CHOUETTE_REFERENTIAL, String.class);

        PrevalidatedFileMetadataProcessor.Metadata metadata =
                fileMetadata.describe(message.getHeader(FILE_NAME, String.class), referential);
        message.setHeader(FILTERING_FILE_CREATED_TIMESTAMP, metadata.createdAt().toString());
        internalBlobStore.uploadBlobWithoutVersionHeader(metadata.fileHandle(),
                new ByteArrayInputStream(metadata.json().getBytes(StandardCharsets.UTF_8)));

        String lastPrevalidated = BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES
                + referential + "-" + CURRENT_PREVALIDATED_NETEX_FILENAME;
        message.setHeader(TARGET_FILE_HANDLE, lastPrevalidated);
        internalBlobStore.copyBlobInBucket(dataset, lastPrevalidated);
    }
}

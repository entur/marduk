package no.rutebanken.marduk.netex;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.services.MardukPublicBlobStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.TARGET_CONTAINER;
import static no.rutebanken.marduk.Constants.TARGET_FILE_HANDLE;

/**
 * Keeps a uniquely named copy of a published NeTEx export in the marduk-exchange bucket, for the codespaces
 * whose dated service journey ids are generated from it.
 *
 * <p>Was {@code direct:copyDatedExport}.
 */
@Component
public class DatedExportUpload {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatedExportUpload.class);

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final MardukPublicBlobStoreService publicBlobStore;
    private final String blobStorePath;
    private final String exchangeContainer;

    public DatedExportUpload(
            MardukPublicBlobStoreService publicBlobStore,
            @Value("${netex.export.dated.version.path:outbound/dated}") String blobStorePath,
            @Value("${blobstore.gcs.exchange.container.name}") String exchangeContainer) {
        this.publicBlobStore = publicBlobStore;
        this.blobStorePath = blobStorePath;
        this.exchangeContainer = exchangeContainer;
    }

    public void copyDatedExport(MardukMessage message) {
        String referential = message.getHeader(CHOUETTE_REFERENTIAL, String.class);
        String datedVersionFileName = referential + "-" + LocalDateTime.now().format(TIMESTAMP) + ".zip";
        LOGGER.info("Start copying dated version of {} to marduk-exchange", datedVersionFileName);

        String fileHandle = Constants.BLOBSTORE_PATH_OUTBOUND + "netex/" + referential + "-"
                + Constants.CURRENT_AGGREGATED_NETEX_FILENAME;
        String targetFileHandle = blobStorePath + "/" + datedVersionFileName;
        // Recorded on the message because the route set them as headers, so they travel on to the
        // notifications the publication sends afterwards.
        message.setHeader(FILE_HANDLE, fileHandle);
        message.setHeader(TARGET_FILE_HANDLE, targetFileHandle);
        message.setHeader(TARGET_CONTAINER, exchangeContainer);

        publicBlobStore.copyBlobToAnotherBucket(fileHandle, exchangeContainer, targetFileHandle);
    }
}

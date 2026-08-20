package no.rutebanken.marduk.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pipeline.RetryPolicy;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The half the two NeTEx exports share: fetching the archive Chouette produced and storing it.
 *
 * <p>Streamed through a temp file rather than held in memory. A full NeTEx export is hundreds of megabytes
 * and the pod's limit is smaller; the routes got the same effect from {@code .streamCaching()} with
 * {@code camel.main.streamCachingSpoolEnabled}.
 */
@Component
public class ChouetteNetexExport {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChouetteNetexExport.class);

    private final ChouetteClient chouetteClient;
    private final MardukInternalBlobStoreService internalBlobStore;
    private final RetryPolicy retryPolicy;

    public ChouetteNetexExport(
            ChouetteClient chouetteClient,
            MardukInternalBlobStoreService internalBlobStore,
            RetryPolicy retryPolicy) {
        this.chouetteClient = chouetteClient;
        this.internalBlobStore = internalBlobStore;
        this.retryPolicy = retryPolicy;
    }

    /**
     * Downloads the export and stores it, recording the handle and the generation on the message.
     *
     * @param fileHandle where the archive is stored, which is also what antu is asked to validate
     */
    public void store(MardukMessage message, String fileHandle) {
        String dataUrl = message.getHeader("data_url", String.class);
        LOGGER.debug("Downloading NeTEx export data from {}", dataUrl);
        Path downloaded = temporaryFile();
        try {
            chouetteClient.downloadTo(dataUrl, downloaded);
            message.setHeader(Constants.FILE_HANDLE, fileHandle);
            // The stream is opened per attempt: a retry of an upload that failed halfway through cannot
            // re-read a consumed one.
            message.setHeader(Constants.FILE_VERSION, retryPolicy.call("Storing " + fileHandle, () -> {
                try (InputStream archive = Files.newInputStream(downloaded)) {
                    return internalBlobStore.uploadBlob(fileHandle, archive);
                }
            }));
            message.setBody("");
        } finally {
            deleteQuietly(downloaded);
        }
    }

    /**
     * Turns Chouette's "nothing to export" failure into the error code an operator recognises.
     *
     * <p>The routes read the failure off the action report left in the exchange body, which by then held the
     * <em>validation</em> report whenever the job produced one - so the code was set only for exports without
     * a validation report. The poller passes the failure code on directly, so it is now set whenever Chouette
     * reports it.
     */
    public void recordAnEmptyExport(MardukMessage message, String errorCode) {
        if (JobEvent.CHOUETTE_JOB_FAILURE_CODE_NO_DATA_PROCEEDED
                .equals(message.getHeader(ChouetteJobPoller.CHOUETTE_FAILURE_CODE, String.class))) {
            message.setHeader(Constants.JOB_ERROR_CODE, errorCode);
        }
    }

    private static Path temporaryFile() {
        try {
            return Files.createTempFile("chouette-netex-export-", ".zip");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create a temporary file for the NeTEx export", e);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOGGER.warn("Could not delete the temporary NeTEx export {}", file, e);
        }
    }
}

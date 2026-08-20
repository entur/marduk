package no.rutebanken.marduk.otp;

import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * The folder in the internal bucket an OTP2 graph build writes into.
 *
 * <p>The build runs as a Kubernetes job that reads and writes GCS directly, so "working directory" means a
 * prefix in the internal bucket rather than anything local. Replaces {@code direct:remoteOtp2CleanUp}, which
 * both graph builds called although only the NeTEx one declared it.
 */
@Component
public class Otp2GraphWorkDirectory {

    private static final Logger LOGGER = LoggerFactory.getLogger(Otp2GraphWorkDirectory.class);

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final MardukInternalBlobStoreService internalBlobStore;
    private final String blobStoreSubdirectory;
    private final boolean deleteAfterBuild;

    public Otp2GraphWorkDirectory(
            MardukInternalBlobStoreService internalBlobStore,
            @Value("${otp.graph.blobstore.subdirectory:graphs}") String blobStoreSubdirectory,
            @Value("${otp.graph.build.remote.work.dir.cleanup:true}") boolean deleteAfterBuild) {
        this.internalBlobStore = internalBlobStore;
        this.blobStoreSubdirectory = blobStoreSubdirectory;
        this.deleteAfterBuild = deleteAfterBuild;
    }

    /**
     * What {@code ${date:now:yyyyMMddHHmmssSSS}} produced. It names the work directory and the graph file,
     * and it is also the correlation id the graph build's status events carry.
     */
    public static String timestamp() {
        return LocalDateTime.now().format(TIMESTAMP);
    }

    /**
     * A fresh directory for one build.
     *
     * <p>The UUID was a route-definition-time constant, so every build since a given startup shared it and
     * only the timestamp told two builds apart. Generated per build here, which is what the expression
     * reads as and what keeps two builds in the same millisecond apart.
     */
    public String path(String timestamp) {
        return blobStoreSubdirectory + "/work/" + UUID.randomUUID() + "/" + timestamp;
    }

    /** The directory the published graphs live under, which is the same bucket subdirectory. */
    public String blobStoreSubdirectory() {
        return blobStoreSubdirectory;
    }

    public void delete(String workDir) {
        if (!deleteAfterBuild) {
            return;
        }
        LOGGER.info("Deleting OTP2 remote work directory {} ...", workDir);
        internalBlobStore.deleteAllBlobsInFolder(workDir);
        LOGGER.info("Deleted OTP2 remote work directory {}", workDir);
    }
}

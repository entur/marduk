package no.rutebanken.marduk.otp;

import no.rutebanken.marduk.domain.OtpGraphsInfo;
import no.rutebanken.marduk.graph.OtpGraphFilesBuilder;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import no.rutebanken.marduk.services.OtpGraphsBlobStoreService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static no.rutebanken.marduk.Constants.OTP2_BASE_GRAPH_OBJ_PREFIX;
import static no.rutebanken.marduk.Constants.OTP2_NETEX_GRAPH_DIR;
import static no.rutebanken.marduk.Constants.OTP2_STREET_GRAPH_DIR;

/**
 * What graphs are in storage.
 *
 * <p>Replaces {@code direct:listGraphs}. The two graph kinds live in different buckets and encode their
 * serialization id differently, which is why there are two regexes.
 */
@Component
public class OtpGraphs {

    private final OtpGraphsBlobStoreService otpGraphsBlobStore;
    private final MardukInternalBlobStoreService internalBlobStore;
    private final Pattern netexGraphFileName;
    private final Pattern streetGraphFileName;
    private final String streetGraphFolder;

    public OtpGraphs(
            OtpGraphsBlobStoreService otpGraphsBlobStore,
            MardukInternalBlobStoreService internalBlobStore,
            @Value("${otp.graph.blobstore.subdirectory:graphs}") String blobStoreGraphSubdirectory) {
        this.otpGraphsBlobStore = otpGraphsBlobStore;
        this.internalBlobStore = internalBlobStore;
        this.netexGraphFileName = Pattern.compile(OTP2_NETEX_GRAPH_DIR + "/" + "(.*)" + "/.*\\.obj");
        this.streetGraphFileName = Pattern.compile(blobStoreGraphSubdirectory + "/" + OTP2_STREET_GRAPH_DIR
                + "/" + OTP2_BASE_GRAPH_OBJ_PREFIX + "-(.*).*\\.obj");
        this.streetGraphFolder = blobStoreGraphSubdirectory + "/" + OTP2_STREET_GRAPH_DIR + "/";
    }

    /** The latest street graph and transit graph for each of the two most recent serialization ids. */
    public OtpGraphsInfo list() {
        List<OtpGraphsInfo.OtpGraphFile> transitGraphs = new OtpGraphFilesBuilder()
                .withFileNameRegex(netexGraphFileName)
                .withFiles(otpGraphsBlobStore.listBlobsInFolders(Set.of(OTP2_NETEX_GRAPH_DIR)).getFiles())
                .build();
        List<OtpGraphsInfo.OtpGraphFile> streetGraphs = new OtpGraphFilesBuilder()
                .withFileNameRegex(streetGraphFileName)
                .withFiles(internalBlobStore.listBlobsInFolders(Set.of(streetGraphFolder)).getFiles())
                .build();
        return new OtpGraphsInfo(streetGraphs, transitGraphs);
    }
}

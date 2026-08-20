package no.rutebanken.marduk.otp;

import static no.rutebanken.marduk.Constants.OTP2_GRAPH_OBJ_PREFIX;
import static no.rutebanken.marduk.Constants.OTP2_NETEX_GRAPH_DIR;
import static no.rutebanken.marduk.Constants.OTP2_STREET_GRAPH_DIR;

/**
 * Where a freshly built OTP2 graph is copied to.
 *
 * <p>Replaces {@code Otp2BaseGraphPublishingProcessor} and {@code Otp2NetexGraphPublishingProcessor}, which
 * did nothing but compute these paths and write them to headers the next route step read.
 */
public final class Otp2GraphPublishing {

    private Otp2GraphPublishing() {
    }

    /**
     * The street graph keeps the name the builder gave it, which carries the compatibility version, and
     * lands in one directory for all versions - so rebuilding the same version overwrites it.
     */
    public record BaseGraph(String builtPath, String publishedPath) {
    }

    /**
     * The transit graph lands in a directory named after its compatibility version and is prefixed with the
     * build timestamp, so every build is kept.
     *
     * @param reportVersion the folder the build report is copied to, and what the current-report page points at
     */
    public record NetexGraph(String compatibilityVersion, String builtPath, String publishedPath,
                             String reportVersion) {
    }

    public static BaseGraph baseGraph(String workDir, String graphFileName, String blobStoreSubdirectory) {
        return new BaseGraph(
                workDir + "/" + graphFileName,
                blobStoreSubdirectory + "/" + OTP2_STREET_GRAPH_DIR + "/" + graphFileName);
    }

    public static NetexGraph netexGraph(String workDir, String timestamp, String graphFileName) {
        String compatibilityVersion = graphCompatibilityVersion(graphFileName);
        return new NetexGraph(
                compatibilityVersion,
                workDir + "/" + graphFileName,
                OTP2_NETEX_GRAPH_DIR + "/" + compatibilityVersion + "/" + timestamp + '-' + graphFileName,
                OTP2_NETEX_GRAPH_DIR + "/" + timestamp + "-report");
    }

    /**
     * The version the builder put in the file name, as in {@code Graph-otp2-EN-0051.obj}. A name that does
     * not carry one is published under {@code unknown-version} rather than rejected.
     */
    public static String graphCompatibilityVersion(String graphFileName) {
        if (graphFileName.startsWith(OTP2_GRAPH_OBJ_PREFIX + "-")) {
            return graphFileName.substring(OTP2_GRAPH_OBJ_PREFIX.length() + 1, graphFileName.lastIndexOf('.'));
        }
        return "unknown-version";
    }
}

package no.rutebanken.marduk.routes.otp.otp2;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import static no.rutebanken.marduk.Constants.*;

/**
 * Camel processor that constructs the file name of the newly built NeTEx graph.
 * The new graph is saved in a directory whose name is the compatibility version of the graph (example: EN-0051).
 * The compatibility version is extracted from the name of the graph file produced by the graph builder (example: Graph-otp2-EN-0051.obj).
 */
public class Otp2NetexGraphPublishingProcessor implements Processor {

    private final String otpGraphsBucketName;

    public Otp2NetexGraphPublishingProcessor(String otpGraphsBucketName) {
        this.otpGraphsBucketName = otpGraphsBucketName;
    }


    @Override
    public void process(Exchange e) {
        BlobStoreFiles.File file = e.getIn().getBody(BlobStoreFiles.File.class);
        if(file == null) {
            throw new IllegalStateException("File not found in message body");
        }
        String graphFileName = file.getFileNameOnly();
        String graphCompatibilityVersion = getGraphCompatibilityVersion(graphFileName);
        String builtOtpGraphPath = e.getProperty(OTP_REMOTE_WORK_DIR, String.class) + "/" + graphFileName;
        String publishedGraphPath = Constants.OTP2_NETEX_GRAPH_DIR
                + "/" + graphCompatibilityVersion
                + "/" + e.getProperty(TIMESTAMP, String.class)
                + '-' + graphFileName;

        String publishedGraphVersion = Constants.OTP2_NETEX_GRAPH_DIR + "/" + e.getProperty(TIMESTAMP, String.class) + "-report";

        e.getIn().setHeader(GRAPH_COMPATIBILITY_VERSION, graphCompatibilityVersion);
        e.getIn().setHeader(FILE_HANDLE, builtOtpGraphPath);
        e.getIn().setHeader(TARGET_FILE_HANDLE, publishedGraphPath);
        e.getIn().setHeader(TARGET_CONTAINER, otpGraphsBucketName);
        e.setProperty(OTP_GRAPH_VERSION, publishedGraphVersion);
    }

    protected static String getGraphCompatibilityVersion(String graphFileName) {
        if (graphFileName.startsWith(OTP2_GRAPH_OBJ_PREFIX + "-")) {
            return graphFileName.substring(OTP2_GRAPH_OBJ_PREFIX.length() + 1, graphFileName.lastIndexOf('.'));
        } else {
            return "unknown-version";
        }
    }

}

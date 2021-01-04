package no.rutebanken.marduk.routes.otp.otp2;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import static no.rutebanken.marduk.Constants.BLOBSTORE_MAKE_BLOB_PUBLIC;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.GRAPH_COMPATIBILITY_VERSION;
import static no.rutebanken.marduk.Constants.OTP2_GRAPH_OBJ_PREFIX;
import static no.rutebanken.marduk.Constants.OTP_REMOTE_WORK_DIR;
import static no.rutebanken.marduk.Constants.TARGET_CONTAINER;
import static no.rutebanken.marduk.Constants.TARGET_FILE_HANDLE;
import static no.rutebanken.marduk.Constants.TIMESTAMP;

/**
 * Camel processor that prepares the graph file,
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

        e.getIn().setHeader(GRAPH_COMPATIBILITY_VERSION, graphCompatibilityVersion);
        e.getIn().setHeader(FILE_HANDLE, builtOtpGraphPath);
        e.getIn().setHeader(TARGET_FILE_HANDLE, publishedGraphPath);
        e.getIn().setHeader(TARGET_CONTAINER, otpGraphsBucketName);
        e.getIn().setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, false);
    }

    protected static String getGraphCompatibilityVersion(String graphFileName) {
        if (graphFileName.startsWith(OTP2_GRAPH_OBJ_PREFIX + "-")) {
            return graphFileName.substring(OTP2_GRAPH_OBJ_PREFIX.length() + 1, graphFileName.lastIndexOf('.'));
        } else {
            return "unknown-version";
        }


    }

}

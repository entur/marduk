package no.rutebanken.marduk.routes.otp.otp2;

import no.rutebanken.marduk.domain.BlobStoreFiles;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.OTP2_BASE_GRAPH_OBJ;
import static no.rutebanken.marduk.Constants.OTP_BUILD_CANDIDATE;
import static no.rutebanken.marduk.Constants.OTP_REMOTE_WORK_DIR;
import static no.rutebanken.marduk.Constants.TARGET_FILE_HANDLE;

/**
 * Camel processor that prepares the graph file,
 */
public class Otp2BaseGraphPublishingProcessor implements Processor {

    private final String blobStoreSubdirectory;

    public Otp2BaseGraphPublishingProcessor(String blobStoreSubdirectory) {
        this.blobStoreSubdirectory = blobStoreSubdirectory;
    }

    @Override
    public void process(Exchange e) {
        BlobStoreFiles.File file = e.getIn().getBody(BlobStoreFiles.File.class);
        if (file == null) {
            throw new IllegalStateException("File not found in message body");
        }
        String graphFileName = file.getFileNameOnly();
        String builtBaseGraphPath = e.getProperty(OTP_REMOTE_WORK_DIR, String.class) + "/" + graphFileName;
        String publishedBaseGraphPath;
        if (e.getProperty(OTP_BUILD_CANDIDATE) != null) {
            publishedBaseGraphPath = blobStoreSubdirectory + "/candidate-" + OTP2_BASE_GRAPH_OBJ;
        } else {
            publishedBaseGraphPath = blobStoreSubdirectory + "/" + OTP2_BASE_GRAPH_OBJ;
        }

        e.getIn().setHeader(FILE_HANDLE, builtBaseGraphPath);
        e.getIn().setHeader(TARGET_FILE_HANDLE, publishedBaseGraphPath);
    }
}

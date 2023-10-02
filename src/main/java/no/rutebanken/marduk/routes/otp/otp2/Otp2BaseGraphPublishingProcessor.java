package no.rutebanken.marduk.routes.otp.otp2;

import no.rutebanken.marduk.domain.BlobStoreFiles;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import static no.rutebanken.marduk.Constants.*;

/**
 * Camel processor that constructs the file name of the newly built street graph.
 * The street graph is saved in a directory named "street" and has the same name as the file produced by the graph builder.
 * Since the file name contains the compatibility version (example: EN-0051), one street graph is saved per compatibility version (example: street-graph-otp2-EN-0051.obj).
 * A pre-existing street graph with the same compatibility version will be overwritten.
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
        publishedBaseGraphPath = blobStoreSubdirectory + "/street/" + graphFileName;

        e.getIn().setHeader(FILE_HANDLE, builtBaseGraphPath);
        e.getIn().setHeader(TARGET_FILE_HANDLE, publishedBaseGraphPath);
    }

}

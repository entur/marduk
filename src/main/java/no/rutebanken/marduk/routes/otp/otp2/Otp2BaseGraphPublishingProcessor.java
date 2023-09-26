package no.rutebanken.marduk.routes.otp.otp2;

import no.rutebanken.marduk.domain.BlobStoreFiles;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.Constants.OTP2_GRAPH_OBJ_PREFIX;

/**
 * Camel processor that builds base graph file names.
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

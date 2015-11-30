package no.rutebanken.marduk.routes.sftp;


import no.rutebanken.marduk.routes.BaseRoute;
import no.rutebanken.marduk.routes.sftp.beans.FileTypeClassifierBean;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ValidationException;
import org.springframework.stereotype.Component;

/**
 * Classifies files and performs basic validation
 */
@Component
public class FileClassificationRoute extends BaseRoute {

//    public static final String FILE_HANDLE = "file_handle";  //TODO use this
    public static final String FILETYPE = "file_type";

    @Override
    public void configure() throws Exception {
        super.configure();

        onException(ValidationException.class)
                .handled(true)
                .to("activemq:queue:DeadLetterQueue");

        from("activemq:queue:ProcessFileQueue")
                .to("log:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .pipeline("direct:getBlob", "direct:storeTempFile", "direct:validateAndProcess");

        from("direct:getBlob")
                .setProperty("file_handle", simple("${header.file_handle}"))  //Using property to hold file handle, as jclouds component wipes the header
                .enrich().simple("jclouds:blobstore:filesystem?operation=CamelJcloudsGet&container=test-container&blobName=${header.file_handle}")
                .setHeader("file_handle", simple("${property.file_handle}"));  //restore file_handle header

        from("direct:storeTempFile")
                .to("file:files/tmp2?fileName=${header.file_handle}");

        from("direct:validateAndProcess")
                .from("file:file/tmp2?fileName=${header.file_handle}&readLock=change") // check that file has been written.
                .setBody(simple("${header.file_handle}"))   //reset body again
            .validate().method(FileTypeClassifierBean.class, "validateFile")
                .choice()
                .when(header(FILETYPE).isEqualTo(FileType.GTFS.name()))
                    .log("Posting file handle ${header.file_handle} on queue.")
                    .to("activemq:queue:ProcessGtfsQueue")
                .when(header(FILETYPE).isEqualTo(FileType.INVALID.name()))
                    .log(LoggingLevel.WARN, "File ${header.file_handle} is invalid. Could not process.")
                    .log("Removing blob: ${header.file_handle}")
                    .toD("jclouds:blobstore:filesystem?operation=CamelJcloudsRemoveBlob&container=test-container&blobName=${header.file_handle}")
                .otherwise()
                    .log(LoggingLevel.WARN, "Could not process file ${header.file_handle}")
                    .log("Removing blob: ${header.file_handle}")
                    .toD("jclouds:blobstore:filesystem?operation=CamelJcloudsRemoveBlob&container=test-container&blobName=${header.file_handle}");
    }
}

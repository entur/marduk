package no.rutebanken.marduk.routes.sftp;


import no.rutebanken.marduk.routes.BaseRoute;
import no.rutebanken.marduk.routes.sftp.beans.FileTypeClassifierBean;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Receives file handle, pulls file from blob store, xlassifies files and performs initial validation.
 */
@Component
public class FileClassificationRoute extends BaseRoute {

    public static final String FILETYPE = "file_type";

    @Value("${blobstore.provider}")
    private String provider;

    @Value("${blobstore.containerName}")
    private String containerName;

    @Override
    public void configure() throws Exception {
        super.configure();

        onException(ValidationException.class)
                .handled(true)
                .log(LoggingLevel.WARN, "Could not process file ${header.file_handle}") //TODO Should we keep files in blob store on failure?
                .log("Removing blob: ${header.file_handle}")
                .toD("jclouds:blobstore:" + provider + "?operation=CamelJcloudsRemoveBlob&container=" + containerName + "&blobName=${header.file_handle}")
                 .setBody(simple("${header.file_handle}"))   //remove file data from body
                .to("activemq:queue:DeadLetterQueue");

        from("activemq:queue:ProcessFileQueue")
                .to("log:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .pipeline("direct:getBlob", "direct:validateAndProcess");

        from("direct:getBlob")
                .to("log:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .setProperty("file_handle", simple("${header.file_handle}"))  //Using property to hold file handle, as jclouds component wipes the header
                .toD("jclouds:blobstore:" + provider + "?operation=CamelJcloudsGet&container=" + containerName + "&blobName=${header.file_handle}")
                .to("log:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .setHeader("file_handle", simple("${property.file_handle}"));  //restore file_handle header

        from("direct:validateAndProcess")
            .validate().method(FileTypeClassifierBean.class, "validateFile")
                .choice()
                .when(header(FILETYPE).isEqualTo(FileType.GTFS.name()))
                    .log("Posting file handle ${header.file_handle} on queue.")
                    .setBody(simple("${header.file_handle}"))   //remove file data from body
                    .to("activemq:queue:ProcessGtfsQueue")
                .otherwise()
                    .log("Unexpected file type or invalid file ${header.file_handle}")
                .end();
    }
}

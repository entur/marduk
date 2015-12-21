package no.rutebanken.marduk.routes.sftp;


import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.sftp.beans.FileTypeClassifierBean;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ValidationException;
import org.springframework.stereotype.Component;

/**
 * Receives file handle, pulls file from blob store, xlassifies files and performs initial validation.
 */
@Component
public class FileClassificationRouteBuilder extends BaseRouteBuilder {

    public static final String FILETYPE = "file_type";

    @Override
    public void configure() throws Exception {
        super.configure();

        onException(ValidationException.class)
                .handled(true)
                .log(LoggingLevel.WARN, "Could not process file ${header.file_handle}") //TODO Should we keep files in blob store on failure?
                .to("direct:removeBlob")
                .setBody(simple("${header.file_handle}"))   //remove file data from body
                .to("activemq:queue:DeadLetterQueue");

        from("activemq:queue:ProcessFileQueue")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .pipeline("direct:getBlob", "direct:validateAndProcess");  //TODO use JMS to call getBlob?

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

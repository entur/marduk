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
                .log(LoggingLevel.WARN, getClass().getName(), "Could not process file ${header.file_handle}") //TODO Should we keep files in blob store on failure?
                .to("direct:removeBlob")
                .setBody(simple("${header.file_handle}"))   //remove file data from body
                .to("activemq:queue:DeadLetterQueue");

        from("activemq:queue:ProcessFileQueue")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .to("direct:getBlob")
                .validate().method(FileTypeClassifierBean.class, "validateFile")
                .choice()
                .when(header(FILETYPE).isEqualTo(FileType.GTFS.name()))
                    .log(LoggingLevel.DEBUG, getClass().getName(), "Posting file handle ${header.file_handle} on queue.")
                    .setBody(simple("${header.file_handle}"))   //remove file data from body
                    .to("activemq:queue:ProcessGtfsQueue")
                .otherwise()
                    .log(LoggingLevel.WARN, getClass().getName(), "Unexpected file type or invalid file ${header.file_handle}")
                .end();
    }
}

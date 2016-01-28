package no.rutebanken.marduk.routes.file;


import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.beans.FileTypeClassifierBean;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ValidationException;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_TYPE;

/**
 * Receives file handle, pulls file from blob store, xlassifies files and performs initial validation.
 */
@Component
public class FileClassificationRouteBuilder extends BaseRouteBuilder {

    @Override
    public void configure() throws Exception {
        super.configure();

        onException(ValidationException.class)
                .handled(true)
                .log(LoggingLevel.WARN, getClass().getName(), "Could not process file ${header." + FILE_HANDLE + "}") //TODO Should we keep files in blob store on failure?
                .to("direct:removeBlob")
                .setBody(simple(""))      //remove file data from body
                .to("activemq:queue:DeadLetterQueue");

        from("activemq:queue:ProcessFileQueue")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .to("direct:getBlob")
                .validate().method(FileTypeClassifierBean.class, "validateFile")
                .choice()
                .when(header(FILE_TYPE).isEqualTo(FileType.GTFS.name()))
                    .log(LoggingLevel.DEBUG, getClass().getName(), "Posting file handle ${header." + FILE_HANDLE + "} on queue.")
                    .setBody(simple(""))   //remove file data from body
                    .to("activemq:queue:ProcessGtfsQueue")
                .otherwise()
                    .log(LoggingLevel.WARN, getClass().getName(), "Unexpected file type or invalid file ${header." + FILE_HANDLE + "}")
                .end();
    }
}

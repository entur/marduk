package no.rutebanken.marduk.routes.file;


import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.beans.FileTypeClassifierBean;
import no.rutebanken.marduk.routes.status.Status;
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
                .log(LoggingLevel.WARN, getClass().getName(), "Could not process file ${header." + FILE_HANDLE + "}") 
                //.to("direct:removeBlob") Keep file for now
                .process(e -> Status.addStatus(e, Status.Action.FILE_TRANSFER, Status.State.FAILED))
                .to("direct:updateStatus")
                .setBody(simple(""))      //remove file data from body
                .to("activemq:queue:DeadLetterQueue");

        from("activemq:queue:ProcessFileQueue")
                .process(e -> Status.addStatus(e, Status.Action.FILE_TRANSFER, Status.State.OK))
                .to("direct:updateStatus")
                .to("direct:getBlob")
                .validate().method(FileTypeClassifierBean.class, "validateFile")
                .choice()
                .when(header(FILE_TYPE).isEqualTo(FileType.INVALID.name()))
                    .log(LoggingLevel.WARN, getClass().getName(), "Unexpected file type or invalid file ${header." + FILE_HANDLE + "}")
                .otherwise()
                    .log(LoggingLevel.DEBUG, getClass().getName(), "Posting " + FILE_HANDLE + " ${header." + FILE_HANDLE + "} and " + FILE_TYPE + " ${header." + FILE_TYPE + "} on chouette import queue.")
                    .setBody(simple(""))   //remove file data from body since this is in jclouds blobstore
                    .to("activemq:queue:ChouetteImportQueue")
                .end();
    }
}

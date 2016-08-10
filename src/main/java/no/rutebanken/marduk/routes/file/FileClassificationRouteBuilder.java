package no.rutebanken.marduk.routes.file;


import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_TYPE;

import org.apache.camel.LoggingLevel;
import org.apache.camel.ValidationException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.beans.FileTypeClassifierBean;
import no.rutebanken.marduk.routes.status.Status;

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
                .log(LoggingLevel.INFO, correlation()+"Could not process file ${header." + FILE_HANDLE + "}") 
                //.to("direct:removeBlob") Keep file for now
                .process(e -> Status.addStatus(e, Status.Action.FILE_TRANSFER, Status.State.FAILED))
                .to("direct:updateStatus")
                .setBody(simple(""))      //remove file data from body
                .to("activemq:queue:DeadLetterQueue");

        from("activemq:queue:ProcessFileQueue?transacted=true")
                .process(e -> Status.addStatus(e, Status.Action.FILE_TRANSFER, Status.State.OK))
                .to("direct:updateStatus")
                .to("direct:getBlob")
                .convertBodyTo(byte[].class)
                .validate().method(FileTypeClassifierBean.class, "validateFile")
                .choice()
                .when(header(FILE_TYPE).isEqualTo(FileType.INVALID.name()))
                    .log(LoggingLevel.WARN, correlation()+"Unexpected file type or invalid file ${header." + FILE_HANDLE + "}")
                .when(header(FILE_TYPE).isEqualTo(FileType.RAR.name()))
                	.log(LoggingLevel.INFO, correlation()+"Splitting and repackaging file ${header." + FILE_HANDLE + "}")
                	.to("direct:splitRarFile")
                .otherwise()
                    .log(LoggingLevel.INFO, correlation()+"Posting " + FILE_HANDLE + " ${header." + FILE_HANDLE + "} and " + FILE_TYPE + " ${header." + FILE_TYPE + "} on chouette import queue.")
                    .setBody(simple(""))   //remove file data from body since this is in jclouds blobstore
                    .to("activemq:queue:ChouetteImportQueue")
                .end()
                .routeId("file-classify");
    
    
        from("direct:splitRarFile")
        	.split(method(RARToZipFilesSplitter.class,"splitRarFile"))
        	.process(e -> {
        		int currentPart = e.getProperty("CamelSplitIndex", Integer.class)+1;
        		String currentPartPadded = StringUtils.leftPad(""+currentPart, 4, '0');
        		String numParts = e.getProperty("CamelSplitSize",String.class);
        		e.getIn().setHeader(FILE_HANDLE, e.getIn().getHeader(FILE_HANDLE)+"_part_"+currentPartPadded+"_of_"+numParts+".zip");
        	})
        	.log(LoggingLevel.INFO, correlation()+"New fragment from RAR file ${header." + FILE_HANDLE + "}")
            .to("direct:uploadBlob")
        	.to("activemq:queue:ProcessFileQueue")
        	.routeId("file-split-rar");

    }
}

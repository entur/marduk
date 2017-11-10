package no.rutebanken.marduk.routes.jms;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.FileType;
import no.rutebanken.marduk.routes.file.beans.FileTypeClassifierBean;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ValidationException;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.*;

/**
 * Receives file notification from "external" queue and uses this to download the file from blob store.
 */
@Component
public class JmsReceiverRouteBuilder extends BaseRouteBuilder {

    @Override
    public void configure() throws Exception {
        super.configure();

        onException(ValidationException.class)
                .handled(true)
                .log(LoggingLevel.INFO, correlation() + "Could not process file ${header." + FILE_HANDLE + "}")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILE_CLASSIFICATION).state(JobEvent.State.FAILED).build())
                .to("direct:updateStatus")
                .setBody(simple(""))      //remove file data from body
                .to("activemq:queue:DeadLetterQueue");


        from("activemq:queue:MardukInboundQueue?transacted=true").streamCaching()
                .transacted()
                .setHeader(Exchange.FILE_NAME, header(Constants.FILE_NAME))
                .log(LoggingLevel.INFO, correlation() + "Received notification about file '${header." + Constants.FILE_NAME + "}' on jms. Fetching file ...")
                .log(LoggingLevel.INFO, correlation() + "Fetching blob ${header." + FILE_HANDLE + "}")
                .to("direct:fetchExternalBlob")
                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .convertBodyTo(byte[].class)
                .validate().method(FileTypeClassifierBean.class, "validateFile")
                .choice()
                .when(header(FILE_TYPE).isEqualTo(FileType.NETEXPROFILE.name()))
                    .log(LoggingLevel.INFO, correlation() + "Ignoring duplicate filter for netexprofile file: ${header." + FILE_HANDLE + "}")
                .otherwise()
                    .to("direct:filterDuplicateFile")
                .end()

                .log(LoggingLevel.INFO, correlation() + "File handle is: ${header." + FILE_HANDLE + "}")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .to("direct:uploadBlob")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILE_TRANSFER).state(JobEvent.State.STARTED).build())
                .to("direct:updateStatus")
                .choice()
                .when(simple("{{blobstore.delete.external.blobs:true}}"))
                .to("direct:deleteExternalBlob")
                .end()
                .to("activemq:queue:ProcessFileQueue");

    }

}

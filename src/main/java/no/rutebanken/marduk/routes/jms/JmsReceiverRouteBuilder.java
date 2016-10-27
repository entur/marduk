package no.rutebanken.marduk.routes.jms;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.Status;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
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

        from("activemq:queue:MardukInboundQueue")
            .log(LoggingLevel.INFO, correlation()+"Received notification about file ${header.CamelFileName} on jms. Fetching file ...")
                .log(LoggingLevel.INFO, correlation() + "Fetching blob ${header." + FILE_HANDLE + "}")
                .to("direct:fetchExternalBlob")
                .process(
                    e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential)
                )
            .log(LoggingLevel.INFO, correlation() + "File handle is: ${header." + FILE_HANDLE + "}")
            .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
            .to("direct:uploadBlob")
            .setHeader(Constants.FILE_NAME, exchangeProperty(Exchange.FILE_NAME))
            .process(e -> Status.builder(e).action(Status.Action.FILE_TRANSFER).state(Status.State.STARTED).build())
            .to("direct:updateStatus")
            .to("direct:deleteExternalBlob")
            .to("activemq:queue:ProcessFileQueue");

    }

}

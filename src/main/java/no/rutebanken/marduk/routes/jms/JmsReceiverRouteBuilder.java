package no.rutebanken.marduk.routes.jms;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

import org.apache.camel.LoggingLevel;
import org.jboss.poc.camelblob.BlobMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.Status;
import no.rutebanken.marduk.routes.status.Status.Action;
import no.rutebanken.marduk.routes.status.Status.State;

/**
 * Receives file from "external" queue, using ActiveMQ blob message. Then putting it in jcouds blob store and posting handle on queue.
 */
@Component
public class JmsReceiverRouteBuilder extends BaseRouteBuilder {

    @Bean
    public BlobMessageConverter blobMessageConverter() {
        return new BlobMessageConverter();
    }

    @Override
    public void configure() throws Exception {
        super.configure();

        from("activemq:queue:ExternalFileUploadQueue?disableReplyTo=true&messageConverter=#blobMessageConverter")
            .log(LoggingLevel.INFO, correlation()+"Received file ${header.CamelFileName} on jms. Storing file ...")
            .process(
                e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential)
            )
            .setHeader(FILE_HANDLE, simple(Constants.BLOBSTORE_PATH_INBOUND_RECEIVED+"${header." + CHOUETTE_REFERENTIAL + "}/${header." + CHOUETTE_REFERENTIAL + "}-${date:now:yyyyMMddHHmmss}-${header.CamelFileName}"))
            .log(LoggingLevel.INFO, correlation()+"File handle is: ${header." + FILE_HANDLE + "}")
            .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
            .to("direct:uploadBlob")
            .process(e -> Status.addStatus(e, Action.FILE_TRANSFER, State.OK))
            .to("direct:updateStatus")
            .to("activemq:queue:ProcessFileQueue");
    }

}

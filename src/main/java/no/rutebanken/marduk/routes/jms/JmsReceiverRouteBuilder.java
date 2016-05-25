package no.rutebanken.marduk.routes.jms;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.Status;
import org.apache.camel.LoggingLevel;
import org.jboss.poc.camelblob.BlobMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.routes.status.Status.Action;
import static no.rutebanken.marduk.routes.status.Status.State;

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
            .log(LoggingLevel.INFO, getClass().getName(), "Received file ${header.CamelFileName} on jms receiver route for provider ${header.RutebankenProviderId} and correlation id ${header.RutebankenCorrelationId}. Storing file ...")
            .process(
                e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)))
            )
            .setHeader(FILE_HANDLE, simple("inbound/received/${header." + CHOUETTE_REFERENTIAL + "}/${header." + CHOUETTE_REFERENTIAL + "}-${date:now:yyyyMMddHHmmss}-${header.CamelFileName}"))
            .log(LoggingLevel.INFO, getClass().getName(), "File handle is: ${header." + FILE_HANDLE + "}")
            .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
            .to("direct:uploadBlob")
            .process(e -> Status.addStatus(e, Action.FILE_TRANSFER, State.OK))
            .to("direct:updateStatus")
            .to("activemq:queue:ProcessFileQueue");
    }

}

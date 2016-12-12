package no.rutebanken.marduk.routes.blobstore;


import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.LoggingLevel;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;

@Component
public class ExternalBlobStoreRoute extends BaseRouteBuilder {

    @Override
    public void configure() throws Exception {
        from("direct:fetchExternalBlob")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .bean("exchangeBlobStoreService","getBlob")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true");

        from("direct:deleteExternalBlob")
                .log(LoggingLevel.INFO, correlation() + "Deleting blob ${header." + FILE_HANDLE + "} from external blob store.")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .bean("exchangeBlobStoreService","deleteBlob")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true");

    }
}

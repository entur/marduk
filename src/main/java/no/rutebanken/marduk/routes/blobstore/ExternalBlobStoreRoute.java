package no.rutebanken.marduk.routes.blobstore;


import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.LoggingLevel;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

@Component
public class ExternalBlobStoreRoute extends BaseRouteBuilder {

    @Override
    public void configure() throws Exception {
        from("direct:fetchExternalBlob")
                .setProperty(FILE_NAME, header(FILE_NAME))
                .setProperty(FILE_HANDLE, header(FILE_HANDLE))
                .setProperty(PROVIDER_ID, header(PROVIDER_ID))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .bean("exchangeBlobStoreService","getBlob")
                .setHeader(FILE_HANDLE, exchangeProperty(FILE_HANDLE))
                .setHeader(FILE_NAME, exchangeProperty(FILE_NAME))
                .setHeader(PROVIDER_ID, exchangeProperty(PROVIDER_ID))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true");

        from("direct:deleteExternalBlob")
                .log(LoggingLevel.INFO, correlation() + "Deleting blob ${header." + FILE_HANDLE + "} from blob store.")
                .setProperty(FILE_NAME, header(FILE_NAME))
                .setProperty(FILE_HANDLE, header(FILE_HANDLE))
                .setProperty(PROVIDER_ID, header(PROVIDER_ID))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .bean("exchangeBlobStoreService","deleteBlob")
                .setHeader(FILE_HANDLE, exchangeProperty(FILE_HANDLE))
                .setHeader(FILE_NAME, exchangeProperty(FILE_NAME))
                .setHeader(PROVIDER_ID, exchangeProperty(PROVIDER_ID))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true");

    }
}

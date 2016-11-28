package no.rutebanken.marduk.routes.blobstore;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.*;

@Component
public class BlobStoreRoute extends BaseRouteBuilder {

    @Override
    public void configure() throws Exception {

        from("direct:uploadBlob")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                //Using properties to hold headers, as bean component wipes them
                .choice()
                    .when(header(BLOBSTORE_MAKE_BLOB_PUBLIC).isNull())
                    .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(false))     //defaulting to false if not specified
                .end()
                .setProperty(FILE_HANDLE, header(FILE_HANDLE))
                .setProperty(FILE_NAME, header(FILE_NAME))
                .setProperty(FILE_TYPE, header(FILE_TYPE))
                .setProperty(PROVIDER_ID, header(PROVIDER_ID))
                .setProperty(CORRELATION_ID, header(CORRELATION_ID))
                .setProperty(CHOUETTE_REFERENTIAL, header(CHOUETTE_REFERENTIAL))
                .setProperty(FILE_TARGET_MD5, header(FILE_TARGET_MD5))
                .setProperty(Exchange.FILE_NAME, header(Exchange.FILE_NAME))
                .setProperty(Exchange.FILE_PARENT, header(Exchange.FILE_PARENT))
                .bean("blobStoreService","uploadBlob")
                .setBody(simple(""))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                //restore headers from properties
                .setHeader(FILE_HANDLE, exchangeProperty(FILE_HANDLE))
                .setHeader(FILE_NAME, exchangeProperty(FILE_NAME))
                .setHeader(FILE_TYPE, exchangeProperty(FILE_TYPE))
                .setHeader(PROVIDER_ID, exchangeProperty(PROVIDER_ID))
                .setHeader(CORRELATION_ID, exchangeProperty(CORRELATION_ID))
                .setHeader(CHOUETTE_REFERENTIAL, exchangeProperty(CHOUETTE_REFERENTIAL))
                .setHeader(FILE_TARGET_MD5, exchangeProperty(FILE_TARGET_MD5))
                .setHeader(Exchange.FILE_NAME, exchangeProperty(Exchange.FILE_NAME))
                .setHeader(Exchange.FILE_PARENT, exchangeProperty(Exchange.FILE_PARENT))
                .log(LoggingLevel.INFO,correlation()+"Stored file ${header." + FILE_HANDLE + "} in blob store.")
                .routeId("blobstore-upload");

        from("direct:getBlob")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                //Using properties to hold headers, as bean component wipes them
                .setProperty(FILE_HANDLE, header(FILE_HANDLE))
                .setProperty(FILE_NAME, header(FILE_NAME))
                .setProperty(FILE_TYPE, header(FILE_TYPE))
                .setProperty(PROVIDER_ID, header(PROVIDER_ID))
                .setProperty(CORRELATION_ID, header(CORRELATION_ID))
                .setProperty(CHOUETTE_REFERENTIAL, header(CHOUETTE_REFERENTIAL))
                .setProperty(FILE_TARGET_MD5, header(FILE_TARGET_MD5))
                .setProperty(Exchange.FILE_NAME, header(Exchange.FILE_NAME))
                .setProperty(Exchange.FILE_PARENT, header(Exchange.FILE_PARENT))
                .bean("blobStoreService","getBlob")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                //restore headers from properties
                .setHeader(FILE_HANDLE, exchangeProperty(FILE_HANDLE))
                .setHeader(FILE_NAME, exchangeProperty(FILE_NAME))
                .setHeader(FILE_TYPE, exchangeProperty(FILE_TYPE))
                .setHeader(PROVIDER_ID, exchangeProperty(PROVIDER_ID))
                .setHeader(CORRELATION_ID, exchangeProperty(CORRELATION_ID))
                .setHeader(CHOUETTE_REFERENTIAL, exchangeProperty(CHOUETTE_REFERENTIAL))
                .setHeader(FILE_TARGET_MD5, exchangeProperty(FILE_TARGET_MD5))
                .setHeader(Exchange.FILE_NAME, exchangeProperty(Exchange.FILE_NAME))
                .setHeader(Exchange.FILE_PARENT, exchangeProperty(Exchange.FILE_PARENT))
                .log(LoggingLevel.INFO, correlation()+ "Returning from fetching file ${header." + FILE_HANDLE + "} from blob store.")
                .routeId("blobstore-download");

        from("direct:listBlobs")
        	// TODO make this route work
	        .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
            .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
	        .bean("blobStoreService","listBlobs")
	        .to("log:" + getClass().getName() + "?level=INFO&showAll=true&multiline=true")
	        .log(LoggingLevel.INFO,correlation()+"Returning from fetching file list from blob store.")
            .routeId("blobstore-list");

}
}

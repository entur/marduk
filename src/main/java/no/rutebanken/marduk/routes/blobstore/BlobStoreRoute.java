package no.rutebanken.marduk.routes.blobstore;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.LoggingLevel;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.*;

@Component
public class BlobStoreRoute extends BaseRouteBuilder {

    @Override
    public void configure() throws Exception {

        from("direct:uploadBlob")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .choice()
                    .when(header(BLOBSTORE_MAKE_BLOB_PUBLIC).isNull())
                    .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(false))     //defaulting to false if not specified
                .end()
                .process(e -> e.getIn().setHeaders(e.getIn().getHeaders()))
                .bean("blobStoreService","uploadBlob")
                .setBody(simple(""))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.INFO,correlation()+"Stored file ${header." + FILE_HANDLE + "} in blob store.")
                .routeId("blobstore-upload");

        from("direct:getBlob")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .process(e -> e.getIn().setHeaders(e.getIn().getHeaders()))
                .bean("blobStoreService","getBlob")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.INFO, correlation()+ "Returning from fetching file ${header." + FILE_HANDLE + "} from blob store.")
                .routeId("blobstore-download");

        from("direct:listBlobs")
	        .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
            .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
            .process(e -> e.getIn().setHeaders(e.getIn().getHeaders()))
            .bean("blobStoreService","listBlobs")
	        .to("log:" + getClass().getName() + "?level=INFO&showAll=true&multiline=true")
	        .log(LoggingLevel.INFO,correlation()+"Returning from fetching file list from blob store.")
            .routeId("blobstore-list");

    }
}

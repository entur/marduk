package no.rutebanken.marduk.routes.blobstore;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static no.rutebanken.marduk.Constants.*;

@Component
public class BlobStoreRoute extends BaseRouteBuilder {

    @Value("${blobstore.provider}")
    private String provider;

    @Value("${blobstore.containerName}")
    private String containerName;

    @Override
    public void configure() throws Exception {

        from("direct:uploadBlob")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                //Using properties to hold headers, as jclouds component wipes them
                .setProperty(FILE_HANDLE, header(FILE_HANDLE))
                .setProperty(FILE_TYPE, header(FILE_TYPE))
                .setProperty(PROVIDER_ID, header(PROVIDER_ID))
                .setProperty(CORRELATION_ID, header(CORRELATION_ID))
                .setProperty(CHOUETTE_REFERENTIAL, header(CHOUETTE_REFERENTIAL))
                .setProperty(FILE_TARGET_MD5, header(FILE_TARGET_MD5))
                .setProperty(Exchange.FILE_NAME, header(Exchange.FILE_NAME))
                .setProperty(Exchange.FILE_PARENT, header(Exchange.FILE_PARENT))
                .process(e -> {
                    if (e.getIn().getBody() instanceof InputStream) {
                        InputStream is = e.getIn().getBody(InputStream.class);
                        e.getIn().setBody(IOUtils.toByteArray(is));
                    }
                    e.getOut().setBody(new ByteArrayInputStream(e.getIn().getBody(byte[].class)), InputStream.class);
                    e.getOut().setHeaders(e.getIn().getHeaders());
                })
                .toD("jclouds:blobstore:" + provider + "?operation=CamelJcloudsPut&container=" + containerName + "&blobName=${header." + FILE_HANDLE + "}")
                .setBody(simple(""))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                //restore headers from properties
                .setHeader(FILE_HANDLE, exchangeProperty(FILE_HANDLE))
                .setHeader(FILE_TYPE, exchangeProperty(FILE_TYPE))
                .setHeader(PROVIDER_ID, exchangeProperty(PROVIDER_ID))
                .setHeader(CORRELATION_ID, exchangeProperty(CORRELATION_ID))
                .setHeader(CHOUETTE_REFERENTIAL, exchangeProperty(CHOUETTE_REFERENTIAL))
                .setHeader(FILE_TARGET_MD5, exchangeProperty(FILE_TARGET_MD5))
                .setHeader(Exchange.FILE_NAME, exchangeProperty(Exchange.FILE_NAME))
                .setHeader(Exchange.FILE_PARENT, exchangeProperty(Exchange.FILE_PARENT))
                .log(LoggingLevel.INFO, getClass().getName(), "Stored file ${header." + FILE_HANDLE + "} in blob store.");

        from("direct:getBlob")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                //Using properties to hold headers, as jclouds component wipes them
                .setProperty(FILE_HANDLE, header(FILE_HANDLE))
                .setProperty(FILE_TYPE, header(FILE_TYPE))
                .setProperty(PROVIDER_ID, header(PROVIDER_ID))
                .setProperty(CORRELATION_ID, header(CORRELATION_ID))
                .setProperty(CHOUETTE_REFERENTIAL, header(CHOUETTE_REFERENTIAL))
                .setProperty(FILE_TARGET_MD5, header(FILE_TARGET_MD5))
                .setProperty(Exchange.FILE_NAME, header(Exchange.FILE_NAME))
                .setProperty(Exchange.FILE_PARENT, header(Exchange.FILE_PARENT))
                .toD("jclouds:blobstore:" + provider + "?operation=CamelJcloudsGet&container=" + containerName + "&blobName=${header." + FILE_HANDLE + "}")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                //restore headers from properties
                .setHeader(FILE_HANDLE, exchangeProperty(FILE_HANDLE))
                .setHeader(FILE_TYPE, exchangeProperty(FILE_TYPE))
                .setHeader(PROVIDER_ID, exchangeProperty(PROVIDER_ID))
                .setHeader(CORRELATION_ID, exchangeProperty(CORRELATION_ID))
                .setHeader(CHOUETTE_REFERENTIAL, exchangeProperty(CHOUETTE_REFERENTIAL))
                .setHeader(FILE_TARGET_MD5, exchangeProperty(FILE_TARGET_MD5))
                .setHeader(Exchange.FILE_NAME, exchangeProperty(Exchange.FILE_NAME))
                .setHeader(Exchange.FILE_PARENT, exchangeProperty(Exchange.FILE_PARENT))
                .log(LoggingLevel.INFO, getClass().getName(), "Returning from fetching file ${header." + FILE_HANDLE + "} from blob store.");

        from("direct:removeBlob")
            .log(LoggingLevel.INFO, getClass().getName(), "Removing blob: ${header." + FILE_HANDLE + "}")
                .toD("jclouds:blobstore:" + provider + "?operation=CamelJcloudsRemoveBlob&container=" + containerName + "&blobName=${header." + FILE_HANDLE + "}");

        
        from("direct:listBlobs")
        	// TODO make this route work
	        .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
            .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
	        .bean("blobStoreService","getFiles")
	        .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
	        .log(LoggingLevel.INFO, getClass().getName(), "Returning from fetching file list ${header." + FILE_HANDLE + "} from blob store.");
}
}

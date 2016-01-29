package no.rutebanken.marduk.routes.blobstore;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
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
                .setProperty(PROVIDER_ID, header(PROVIDER_ID))
                .setProperty(CORRELATION_ID, header(CORRELATION_ID))
                .setProperty(Exchange.FILE_NAME, header(Exchange.FILE_NAME))
                .process(e -> {
                    e.getOut().setBody(new ByteArrayInputStream(e.getIn().getBody(byte[].class)), InputStream.class);
                    e.getOut().setHeaders(e.getIn().getHeaders());
                })
                .toD("jclouds:blobstore:" + provider + "?operation=CamelJcloudsPut&container=" + containerName + "&blobName=${header." + FILE_HANDLE + "}")
                .setBody(simple(""))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                //restore headers from properties
                .setHeader(FILE_HANDLE, exchangeProperty(FILE_HANDLE ))
                .setHeader(PROVIDER_ID, exchangeProperty(PROVIDER_ID))
                .setHeader(CORRELATION_ID, exchangeProperty(CORRELATION_ID))
                .setHeader(Exchange.FILE_NAME, exchangeProperty(Exchange.FILE_NAME))
                .log(LoggingLevel.INFO, getClass().getName(), "Stored file ${header." + FILE_HANDLE + "} in blob store.");

        from("direct:getBlob")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                //Using properties to hold headers, as jclouds component wipes them
                .setProperty(FILE_HANDLE, header(FILE_HANDLE))
                .setProperty(PROVIDER_ID, header(PROVIDER_ID))
                .setProperty(CORRELATION_ID, header(CORRELATION_ID))
                .setProperty(Exchange.FILE_NAME, header(Exchange.FILE_NAME))
                .toD("jclouds:blobstore:" + provider + "?operation=CamelJcloudsGet&container=" + containerName + "&blobName=${header." + FILE_HANDLE + "}")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                //restore headers from properties
                .setHeader(FILE_HANDLE, exchangeProperty(FILE_HANDLE))
                .setHeader(PROVIDER_ID, exchangeProperty(PROVIDER_ID))
                .setHeader(CORRELATION_ID, exchangeProperty(CORRELATION_ID))
                .setHeader(Exchange.FILE_NAME, exchangeProperty(Exchange.FILE_NAME))
                .log(LoggingLevel.INFO, getClass().getName(), "Got file ${header." + FILE_HANDLE + "} from blob store.");

        from("direct:removeBlob")
            .log(LoggingLevel.INFO, getClass().getName(), "Removing blob: ${header." + FILE_HANDLE + "}")
                .toD("jclouds:blobstore:" + provider + "?operation=CamelJcloudsRemoveBlob&container=" + containerName + "&blobName=${header." + FILE_HANDLE + "}");
    }
}

package no.rutebanken.marduk.routes.blobstore;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
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
                .setProperty(FILE_HANDLE, simple("${header." + FILE_HANDLE + "}"))  //Using property to hold file handle, as jclouds component wipes the header
                .process(e -> {
                    e.getOut().setBody(new ByteArrayInputStream(e.getIn().getBody(byte[].class)), InputStream.class);
                    e.getOut().setHeaders(e.getIn().getHeaders());
                })
                .toD("jclouds:blobstore:" + provider + "?operation=CamelJcloudsPut&container=" + containerName + "&blobName=${header." + FILE_HANDLE + "}")
                .setBody(simple(""))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .setHeader(FILE_HANDLE, simple("${property." + FILE_HANDLE + "}"))  //restore file_handle header
                .log(LoggingLevel.INFO, getClass().getName(), "Stored file ${header." + FILE_HANDLE + "} in blob store.");

        from("direct:getBlob")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .setProperty(FILE_HANDLE, simple("${header." + FILE_HANDLE + "}"))  //Using property to hold file handle, as jclouds component wipes the header
                .setProperty(PROVIDER_ID, header(PROVIDER_ID))  //Using property to hold file handle, as jclouds component wipes the header
                .toD("jclouds:blobstore:" + provider + "?operation=CamelJcloudsGet&container=" + containerName + "&blobName=${header." + FILE_HANDLE + "}")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .setHeader(FILE_HANDLE, simple("${property." + FILE_HANDLE + "}"))  //restore file_handle header
                .setHeader(PROVIDER_ID, exchangeProperty(PROVIDER_ID)); //restore file_handle header

        from("direct:removeBlob")
            .log(LoggingLevel.INFO, getClass().getName(), "Removing blob: ${header." + FILE_HANDLE + "}")
                .toD("jclouds:blobstore:" + provider + "?operation=CamelJcloudsRemoveBlob&container=" + containerName + "&blobName=${header." + FILE_HANDLE + "}");
    }
}

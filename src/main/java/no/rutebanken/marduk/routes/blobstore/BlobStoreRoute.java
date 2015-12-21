package no.rutebanken.marduk.routes.blobstore;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BlobStoreRoute extends RouteBuilder {

    @Value("${blobstore.provider}")
    private String provider;

    @Value("${blobstore.containerName}")
    private String containerName;

    @Override
    public void configure() throws Exception {

        from("direct:getBlob")
            .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
            .setProperty("file_handle", simple("${header.file_handle}"))  //Using property to hold file handle, as jclouds component wipes the header
            .toD("jclouds:blobstore:" + provider + "?operation=CamelJcloudsGet&container=" + containerName + "&blobName=${header.file_handle}")
            .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
            .setHeader("file_handle", simple("${property.file_handle}"));  //restore file_handle header

        from("direct:uploadBlob")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .toD("jclouds:blobstore:" + provider + "?operation=CamelJcloudsPut&container=" + containerName + "&blobName=${header.file_handle}")
                .setBody(simple("${header.file_handle}"))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log("Stored file in blob store. Putting handle ${header.file_handle} on queue...");


        from("direct:removeBlob")
            .log("Removing blob: ${header.file_handle}")
                .toD("jclouds:blobstore:" + provider + "?operation=CamelJcloudsRemoveBlob&container=" + containerName + "&blobName=${header.file_handle}");
    }
}

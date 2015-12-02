package no.rutebanken.marduk.routes.sftp;

import no.rutebanken.marduk.routes.BaseRoute;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Downloads file from lamassu, putting it in blob store, posting handle on queue.
 */
@Component
public class SftpRoute extends BaseRoute {

    @Value("${blobstore.provider}")
    private String provider;

    @Value("${blobstore.containerName}")
    private String containerName;

    @Override
    public void configure() throws Exception {
        super.configure();

        String user = "nvdb";   //TODO implement solution for checking multiple folders (in addition to nvdb).
                                // Maybe use this: http://stackoverflow.com/questions/10451444/add-camel-route-at-runtime-in-java

        from("sftp://" + user + "@lamassu:22?privateKeyFile=/opt/jboss/.ssh/lamassu.pem&delay=30s&delete=true&localWorkDirectory=files/tmp")
            .log("Received file on route. Storing file ...")
            .setHeader("file_handle", simple("${date:now:yyyyMMddHHmmss}-${header.CamelFileNameOnly}"))
            .log("File handle is: ${header.file_handle}")
                .to("log:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(e -> {
                    e.getOut().setBody(new ByteArrayInputStream(e.getIn().getBody(byte[].class)), InputStream.class);
                    e.getOut().setHeaders(e.getIn().getHeaders());
                })
                .to("direct:uploadBlob");

        from("direct:uploadBlob")
                .to("log:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .toD("jclouds:blobstore:" + provider + "?operation=CamelJcloudsPut&container=" + containerName + "&blobName=${header.file_handle}")
                .setBody(simple("${header.file_handle}"))
                .to("log:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .log("Stored file in blob store. Putting handle ${header.file_handle} on queue...")
                .to("activemq:queue:ProcessFileQueue");

    }

}

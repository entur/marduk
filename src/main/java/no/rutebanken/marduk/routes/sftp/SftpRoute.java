package no.rutebanken.marduk.routes.sftp;

import no.rutebanken.marduk.routes.BaseRoute;
import org.springframework.stereotype.Component;

/**
 * Downloads file from lamassu, putting it in blob store, posting handle on queue.
 */
@Component
public class SftpRoute extends BaseRoute {

    @Override
    public void configure() throws Exception {
        super.configure();

        String user = "nvdb";   //TODO implement solution for checking multiple folders (substitute nvdb)

        from("sftp://" + user + "@lamassu:22?privateKeyFile=/opt/jboss/.ssh/lamassu.pem&delay=30s&delete=true&localWorkDirectory=files/tmp")
            .log("Received file on route. Storing file ...")
            .setHeader("file_handle", simple("${date:now:yyyyMMddHHmmss}-${header.CamelFileNameOnly}"))
            .log("File handle is: ${header.file_handle}")
                .to("log:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .toD("jclouds:blobstore:filesystem?operation=CamelJcloudsPut&container=test-container&blobName=${header.file_handle}")
                .setBody(simple("${header.file_handle}"))
                .to("log:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .log("Storing file in blob store. Putting handle ${header.file_handle} on queue...")
                .to("activemq:queue:ProcessFileQueue");

    }

}

package no.rutebanken.marduk.routes.otp;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.component.http4.HttpMethods;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.util.Date;

import static no.rutebanken.marduk.Constants.*;
import static org.apache.commons.io.FileUtils.deleteDirectory;

@Component
public class GraphPublishRouteBuilder extends BaseRouteBuilder {

    @Value("${otp.graph.deployment.notification.url:none}")
    private String otpGraphDeploymentNotificationUrl;

    @Value("${otp.graph.build.directory}")
    private String otpGraphBuildDirectory;

    @Value("${otp.graph.blobstore.subdirectory}")
    private String blobStoreSubdirectory;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("file:" + otpGraphBuildDirectory + "?fileName=" + GRAPH_OBJ + "&doneFileName=" + GRAPH_OBJ + ".done&recursive=true&noop=true")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Starting graph publishing.")
                .process(
                        e -> e.getIn().setHeader(FILE_HANDLE, blobStoreSubdirectory + "/" + e.getIn().getHeader(Exchange.FILE_NAME, String.class).replace("/", "-"))
                )
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .convertBodyTo(InputStream.class)
                .process(
                        e -> e.getIn().setHeader(FILE_HANDLE, blobStoreSubdirectory + "/" + e.getIn().getHeader(Exchange.FILE_NAME, String.class).replace("/", "-"))
                )
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .to("direct:uploadBlob")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Done uploading new OTP graph.")
                .to("direct:notify")
                .to("direct:cleanUp");

        from("direct:notify")
                .setProperty("notificationUrl", constant(otpGraphDeploymentNotificationUrl))
                .choice()
                    .when(exchangeProperty("notificationUrl").isNotEqualTo("none"))
                        .log(LoggingLevel.INFO, getClass().getName(), "Notifying " + otpGraphDeploymentNotificationUrl + " about new otp graph.")
                        .setHeader(METADATA_DESCRIPTION, constant("Uploaded new Graph object file."))
                        .setHeader(METADATA_FILE, simple("${header." + FILE_HANDLE + "}"))
                        .process(e -> e.getIn().setBody(new Metadata("Uploaded new Graph object file.", e.getIn().getHeader(FILE_HANDLE, String.class), new Date(), Metadata.Status.OK, Metadata.Action.OTP_GRAPH_UPLOAD).getJson()))
                        .removeHeaders("*")
                        .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                        .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                        .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                        .toD("${property.notificationUrl}")
                        .log(LoggingLevel.DEBUG, getClass().getName(), "Done notifying. Got a ${header." + Exchange.HTTP_RESPONSE_CODE + "} back.")
                    .otherwise()
                        .log(LoggingLevel.INFO, getClass().getName(), "No notification url configured for opt graph building. Doing nothing.");

        from("direct:cleanUp")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Deleting build folder ${property." + Exchange.FILE_PARENT + "} ...")
                .process(e -> deleteDirectory(new File(e.getIn().getExchange().getProperty(Exchange.FILE_PARENT, String.class))))
                .log(LoggingLevel.DEBUG, getClass().getName(), "Build folder ${property." + Exchange.FILE_PARENT + "} cleanup done.");
    }
}

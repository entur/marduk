package no.rutebanken.marduk.routes.google;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;

/**
 * Route publishing GTFS export to google
 */
@Component
public class GoogleGtfsPublishRoute extends BaseRouteBuilder {

    /**
     * Every morning at 4 AM.
     */
    @Value("${google.publish.cron.schedule:0+0+4+?+*+*}")
    private String cronSchedule;

    @Value("${google.export.file.name:google_norway-aggregated-gtfs.zip}")
    private String googleExportFileName;


    @Value("${google.publish.sftp.host:partnerupload.google.com}")
    private String googleSftpHost;


    @Value("${google.publish.sftp.port:19321}")
    private int googleSftpPort;


    @Value("${google.publish.sftp.username}")
    private String googleSftpUsername;

    @Value("${google.publish.sftp.password}")
    private String googleSftpPassword;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("quartz2://marduk/googleExportPublish?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{google.publish.scheduler.autoStartup:true}}")
                .filter(e -> isSingletonRouteActive(e.getFromRouteId()))
                .log(LoggingLevel.INFO, "Quartz triggers publish of google gtfs export.")
                .to("activemq:queue:GooglePublishQueue")
                .routeId("google-publish-quartz");


        singletonFrom("activemq:queue:GooglePublishQueue?transacted=true&maxConcurrentConsumers=1&messageListenerContainerFactoryRef=batchListenerContainerFactory").autoStartup("{{google.publish.autoStartup:true}}")
                .transacted()

                .log(LoggingLevel.INFO, getClass().getName(), "Start publish of GTFS file to Google")

                .setHeader(FILE_HANDLE, simple(BLOBSTORE_PATH_OUTBOUND + "gtfs/google/" + googleExportFileName))
                .to("direct:getBlob")
                .setHeader(Exchange.FILE_NAME, constant(googleExportFileName))
                .to("sftp:" + googleSftpUsername + ":" + googleSftpPassword + "@" + googleSftpHost + ":" + googleSftpPort)

                .log(LoggingLevel.INFO, getClass().getName(), "Completed publish of GTFS file to Google")
                .routeId("google-publish-route");


    }
}

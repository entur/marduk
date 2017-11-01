package no.rutebanken.marduk.routes.google;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.*;

/**
 * Route preparing and uploading GTFS export to google
 */
@Component
public class GoogleGtfsExportRoute extends BaseRouteBuilder {

    @Value("${google.export.download.directory:files/google}")
    private String localWorkingDirectory;

    @Value("${gtfs.norway.merged.file.name:rb_norway-aggregated-gtfs.zip}")
    private String gtfsNorwayMergedFileName;

    @Value("${google.export.file.name:google_norway-aggregated-gtfs.zip}")
    private String googleExportFileName;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("activemq:queue:GoogleExportQueue?transacted=true&maxConcurrentConsumers=1&messageListenerContainerFactoryRef=batchListenerContainerFactory").autoStartup("{{google.export.autoStartup:true}}")
                .transacted()

                .log(LoggingLevel.INFO, getClass().getName(), "Start export of GTFS file for Google")

                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.TIMETABLE_PUBLISH).action("EXPORT_GOOGLE_GTFS").fileName(googleExportFileName).state(JobEvent.State.STARTED).newCorrelationId().build()).to("direct:updateStatus")

                .setHeader(Exchange.FILE_PARENT, constant(localWorkingDirectory))
                .to("direct:cleanUpLocalDirectory")

                .to("direct:downloadLatestFullExport")
                .to("direct:transformToGoogleGTFS")
                .to("direct:uploadGoogleGtfs")

                // Do not trigger publish on every build (done as scheduled job).to("activemq:queue:GooglePublishQueue")

                .to("direct:cleanUpLocalDirectory")

                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.OK).build()).to("direct:updateStatus")

                .log(LoggingLevel.INFO, getClass().getName(), "Completed export of GTFS file for Google")
                .routeId("google-export-route");


        from("direct:downloadLatestFullExport")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching " + BLOBSTORE_PATH_OUTBOUND + "gtfs/" + gtfsNorwayMergedFileName)
                .setProperty("fileName", body())
                .setHeader(FILE_HANDLE, simple(BLOBSTORE_PATH_OUTBOUND + "gtfs/" + gtfsNorwayMergedFileName))
                .to("direct:getBlob")
                .choice()
                .when(body().isNotEqualTo(null))
                .toD("file:" + localWorkingDirectory + "/?fileName=" + gtfsNorwayMergedFileName)
                .otherwise()
                .log(LoggingLevel.INFO, getClass().getName(), "${header." + FILE_HANDLE + "} was empty when trying to fetch it from blobstore.")
                .routeId("google-export-get-full-gtfs");

        from("direct:transformToGoogleGTFS")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Merging gtfs files for all providers.")
                .setBody(simple(localWorkingDirectory + "/" + gtfsNorwayMergedFileName))
                .bean("googleGtfsTransformationService")
                .routeId("google-export-transform-gtfs");

        from("direct:uploadGoogleGtfs")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Upload google formatted gtfs file.")
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(true))
                .setHeader(FILE_HANDLE, simple(BLOBSTORE_PATH_OUTBOUND + "gtfs/google/" + googleExportFileName))
                .to("direct:uploadBlob")
                .routeId("google-export-upload-gtfs");
    }
}

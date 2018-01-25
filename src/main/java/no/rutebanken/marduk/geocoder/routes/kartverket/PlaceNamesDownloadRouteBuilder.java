package no.rutebanken.marduk.geocoder.routes.kartverket;

import no.rutebanken.marduk.geocoder.routes.control.GeoCoderTaskType;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.geocoder.GeoCoderConstants.*;

@Component
public class PlaceNamesDownloadRouteBuilder extends BaseRouteBuilder {
    /**
     * One time per 24H on MON-FRI
     */
    @Value("${kartverket.place.names.download.cron.schedule:0+0+23+?+*+MON-FRI}")
    private String cronSchedule;

    @Value("${kartverket.blobstore.subdirectory:kartverket}")
    private String blobStoreSubdirectoryForKartverket;


    @Value("${kartverket.place.names.dataSetId:30caed2f-454e-44be-b5cc-26bb5c0110ca}")
    private String placeNamesDataSetId;

    private static final String FORMAT_SOSI = "SOSI";

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("quartz2://marduk/placeNamesDownload?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{kartverket.place.names.download.autoStartup:false}}")
                .filter(e -> isSingletonRouteActive(e.getFromRouteId()))
                .log(LoggingLevel.INFO, "Quartz triggers download of place names.")
                .setBody(constant(KARTVERKET_PLACE_NAMES_DOWNLOAD))
                .inOnly("direct:geoCoderStart")
                .routeId("place-names-download-quartz");

        from(KARTVERKET_PLACE_NAMES_DOWNLOAD.getEndpoint())
                .log(LoggingLevel.INFO, "Start downloading place names")
                .process(e -> JobEvent.systemJobBuilder(e).startGeocoder(GeoCoderTaskType.PLACE_NAMES_DOWNLOAD).build()).to("direct:updateStatus")
                .to("direct:transferPlaceNamesFiles")
                .choice()
                .when(simple("${header." + CONTENT_CHANGED + "}"))
                .log(LoggingLevel.INFO, "Uploaded updated place names from mapping authority. Initiating update of Tiamat")
                .setBody(constant(null))
                .setProperty(GEOCODER_NEXT_TASK, constant(PELIAS_UPDATE_START))
                .otherwise()
                .log(LoggingLevel.INFO, "Finished downloading place names from mapping authority with no changes")
                .end()
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.OK).build()).to("direct:updateStatus")
                .routeId("place-names-download");


        from("direct:transferPlaceNamesFiles")
                .setHeader(KARTVERKET_DATASETID, constant(placeNamesDataSetId))
                .setHeader(FOLDER_NAME, constant(blobStoreSubdirectoryForKartverket + "/placeNames"))
                .setHeader(KARTVERKET_FORMAT, constant(FORMAT_SOSI))
                .to("direct:uploadUpdatedFiles")
                .routeId("place-names-to-blobstore");

    }


}

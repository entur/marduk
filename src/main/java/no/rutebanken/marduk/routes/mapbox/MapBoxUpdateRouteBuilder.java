package no.rutebanken.marduk.routes.mapbox;


import no.rutebanken.marduk.geocoder.routes.tiamat.TiamatGeoCoderExportRouteBuilder;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;

@Component
public class MapBoxUpdateRouteBuilder extends BaseRouteBuilder {

    private static final String FILE_EXTENSION = "RutebankenFileExtension";
    private static final String CONVERSION_ROUTE = "RutebankenConversionRoute";
    private static final String WORKING_DIRECTORY = "RutebankenWorkingDirectory";
    private static final String TIAMAT_EXPORT_GCP_PATH = "tiamat-export";

    /**
     * One time per 24H on MON-FRI
     */
    @Value("${mapbox.update.cron.schedule:0+0+23+?+*+MON-FRI}")
    private String cronSchedule;

    /**
     * Use the same tiamat data as the geocoder
     */
    @Value("${tiamat.export.blobstore.subdirectory:tiamat/geocoder}")
    private String blobStoreSubdirectoryForTiamatGeoCoderExport;


    @Value("${mapbox.download.directory:files/mapbox}")
    private String localWorkingDirectory;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("quartz2://marduk/mapBoxUpdate?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{mapbox.update.autoStartup:false}}")
                .filter(e -> isLeader(e.getFromRouteId()))
                .log(LoggingLevel.INFO, "Update mapbox tileset")
                .to("direct:downloadUnzipMapboxData")
                .routeId("mapbox-update-quartz");

        from("direct:downloadUnzipMapboxData")
                .setHeader(TIAMAT_EXPORT_GCP_PATH, simple(blobStoreSubdirectoryForTiamatGeoCoderExport + "/" + TiamatGeoCoderExportRouteBuilder.TIAMAT_EXPORT_LATEST_FILE_NAME))
                .to("direct:recreateLocalMapboxDirectory")
                .to("direct:downloadLatestTiamatExportToFolder")
                .to("direct:mapboxUnzipLatestTiamatExportToFolder")
//                .setBody(simple("file://" + localWorkingDirectory + "/tiamat/tiamat-export.xml"))
                
                .routeId("mapbox-download-extract-tiamat-data");

        from("file://"+localWorkingDirectory + "/tiamat/?flatten=true&include=.*xml")
                .to("direct:transformToGeoJsonFromTiamat")
                .to("direct:insertGeoJsonFileToLocalDir")
                .log(LoggingLevel.INFO, "Finished inserting tiamat data")
                .routeId("mapbox-convert-tiamat-data");

        from("direct:downloadLatestTiamatExportToFolder")
                .setHeader(FILE_HANDLE, header(TIAMAT_EXPORT_GCP_PATH))
                .to("direct:getBlob")
                .choice()
                .when(body().isNotEqualTo(null))
                .toD("file:" + localWorkingDirectory + "/tiamat/?fileName=" + TiamatGeoCoderExportRouteBuilder.TIAMAT_EXPORT_LATEST_FILE_NAME)
                .otherwise()
                .log(LoggingLevel.INFO, getClass().getName(), "${header." + FILE_HANDLE + "} was empty when trying to fetch it from blobstore.")
                .routeId("mapbox-download-latest-tiamat-export-to-folder");

        from("direct:mapboxUnzipLatestTiamatExportToFolder")
                .process(e -> ZipFileUtils.unzipFile(new FileInputStream(localWorkingDirectory + "/tiamat/" + TiamatGeoCoderExportRouteBuilder.TIAMAT_EXPORT_LATEST_FILE_NAME), localWorkingDirectory + "/tiamat"))
                .log(LoggingLevel.INFO, "Unzipped file to folder tiamat")
                .routeId("mapbox-unzip-tiamat-export");

        from("direct:recreateLocalMapboxDirectory")
                .setHeader(Exchange.FILE_PARENT, constant(localWorkingDirectory))
                .to("direct:cleanUpLocalDirectory")
                .process(e -> new File(localWorkingDirectory).mkdirs())
                .routeId("mapbox-recreate-mapbox-directory");

        from("direct:transformToGeoJsonFromTiamat")
                .log(LoggingLevel.INFO, "convert tiamat data to geojson")
                .bean("deliveryPublicationStreamToGeoJson", "transform")
                .routeId("mapbox-convert-from-tiamat");

        from("direct:insertGeoJsonFileToLocalDir")
                .setHeader(FILE_HANDLE, simple(blobStoreSubdirectoryForTiamatGeoCoderExport + "/" + TiamatGeoCoderExportRouteBuilder.TIAMAT_EXPORT_LATEST_FILE_NAME))
                .to("direct:getBlob")
                .routeId("mapbox-insert-to-tmp-file");
    }

}

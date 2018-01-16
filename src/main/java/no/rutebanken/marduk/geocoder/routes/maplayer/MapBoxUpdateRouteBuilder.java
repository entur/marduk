package no.rutebanken.marduk.geocoder.routes.maplayer;


import no.rutebanken.marduk.geocoder.routes.tiamat.TiamatGeoCoderExportRouteBuilder;
import no.rutebanken.marduk.geocoder.routes.util.MarkContentChangedAggregationStrategy;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;

@Component
public class MapBoxUpdateRouteBuilder extends BaseRouteBuilder {

    private static final String FILE_EXTENSION = "RutebankenFileExtension";
    private static final String CONVERSION_ROUTE = "RutebankenConversionRoute";
    private static final String WORKING_DIRECTORY = "RutebankenWorkingDirectory";

    /**
     * One time per 24H on MON-FRI
     */
    @Value("${maplayer.update.cron.schedule:0+0+23+?+*+MON-FRI}")
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
                .to("direct:uploadTiamatToMapboxAsGeoJson")
                .routeId("mapbox-update-quartz");


        from("direct:uploadTiamatToMapboxAsGeoJson")
                .to("direct:cleanUpLocalDirectory")
                .process(e -> new File(localWorkingDirectory).mkdirs())
                .setHeader(Exchange.FILE_PARENT, simple(blobStoreSubdirectoryForTiamatGeoCoderExport))
                .setHeader(WORKING_DIRECTORY, simple(localWorkingDirectory + "/tiamat"))
                .setHeader(CONVERSION_ROUTE, constant("direct:convertToGeoJsonFromTiamat"))
                .setHeader(FILE_EXTENSION, constant("xml"))
                .to("direct:insertTiamatExportToTmpFile")
                .log(LoggingLevel.INFO, "Finished inserting tiamat data")
                .end()
                .routeId("mapbox-convert-tiamat-data");

        from("direct:convertToGeoJsonFromTiamat")
                .log(LoggingLevel.INFO, "convertToMapLayerFromTiamat")
                .bean("deliveryPublicationStreamToGeoJson", "transform")
                .routeId("mapbox-convert-from-tiamat");

        from("direct:insertTiamatExportToTmpFile")
                .setHeader(FILE_HANDLE, simple(blobStoreSubdirectoryForTiamatGeoCoderExport + "/" + TiamatGeoCoderExportRouteBuilder.TIAMAT_EXPORT_LATEST_FILE_NAME ))
                .to("direct:getBlob")
                .log(LoggingLevel.INFO, "Updating map data from file: ${header." + FILE_HANDLE + "}")
                .toD("${header." + CONVERSION_ROUTE + "}")
                .end()
                .routeId("map-layer-insert-to-tmp-file");
    }

}

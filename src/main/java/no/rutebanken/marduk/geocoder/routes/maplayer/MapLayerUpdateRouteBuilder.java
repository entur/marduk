package no.rutebanken.marduk.geocoder.routes.maplayer;


import no.rutebanken.marduk.geocoder.routes.util.MarkContentChangedAggregationStrategy;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;

@Component
public class MapLayerUpdateRouteBuilder extends BaseRouteBuilder {

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

    @Value("${pelias.download.directory:files/pelias}")
    private String localWorkingDirectory;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("quartz2://marduk/mapLayerUpdate?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{maplayer.update.autoStartup:false}}")
                .filter(e -> isLeader(e.getFromRouteId()))
                .log(LoggingLevel.INFO, "Create map layer")
                .to("direct:convertTiamatDataMapLayer")
                .routeId("map-layer-update-quartz");


        from("direct:convertTiamatDataMapLayer")
                .log(LoggingLevel.INFO, "Start generating map data layer")
                .setHeader(Exchange.FILE_PARENT, simple(blobStoreSubdirectoryForTiamatGeoCoderExport))
                .setHeader(WORKING_DIRECTORY, simple(localWorkingDirectory + "/tiamat"))
                .setHeader(CONVERSION_ROUTE, constant("direct:convertToMapLayerFromTiamat"))
                .setHeader(FILE_EXTENSION, constant("xml"))
                .to("direct:insertToTmpFile")
                .log(LoggingLevel.INFO, "Finished generating map layer from tiamat data")
                .end()
                .routeId("map-layer-convert-tiamat-data");

        from("direct:convertToMapLayerFromTiamat")
                .log(LoggingLevel.INFO, "convertToMapLayerFromTiamat")
                .bean("deliveryPublicationStreamToMapLayerData", "transform")
                .routeId("map-layer-convert-commands-from-tiamat");

        from("direct:insertToTmpFile")
                .bean("blobStoreService", "listBlobsInFolder")
                .split(simple("${body.files}")).stopOnException()
                .aggregationStrategy(new MarkContentChangedAggregationStrategy())
                .setHeader(FILE_HANDLE, simple("${body.name}"))
                .to("direct:getBlob")
//                .choice()
//                .when(header(FILE_HANDLE).endsWith(".zip"))
//                .to("direct:insertToPeliasFromZipArchive")
//                .otherwise()
                .log(LoggingLevel.INFO, "Updating map data from file: ${header." + FILE_HANDLE + "}")
                .toD("${header." + CONVERSION_ROUTE + "}")
                .end()
                .routeId("map-layer-insert-to-tmp-file");
    }

}

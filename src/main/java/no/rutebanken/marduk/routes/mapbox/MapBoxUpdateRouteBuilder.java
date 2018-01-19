package no.rutebanken.marduk.routes.mapbox;


import no.rutebanken.marduk.geocoder.routes.tiamat.TiamatGeoCoderExportRouteBuilder;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import no.rutebanken.marduk.routes.mapbox.model.MapBoxAwsCredentials;
import no.rutebanken.marduk.routes.mapbox.model.MapBoxUploadStatus;
import no.rutebanken.marduk.routes.mapbox.model.MapboxUploadRequest;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.LOOP_COUNTER;

@Component
public class MapBoxUpdateRouteBuilder extends BaseRouteBuilder {

    private static final String TIAMAT_EXPORT_GCP_PATH = "tiamat-export";
    private static final int POLL_MAPBOX_UPLOAD_STATUS_DELAY = 15000;
    private static final String TIAMAT_GEOSJON_FILENAME = "tiamat.geojson";

    @Value("${mapbox.update.cron.schedule:0+0+23+?+*+MON-FRI}")
    private String cronSchedule;

    /**
     * Use the same tiamat data as the geocoder
     */
    @Value("${tiamat.export.blobstore.subdirectory:tiamat/geocoder}")
    private String blobStoreSubdirectoryForTiamatGeoCoderExport;


    @Value("${mapbox.download.directory:files/mapbox}")
    private String localWorkingDirectory;

    @Value("${mapbox.api.url:https4://api.mapbox.com}")
    private String mapboxApiUrl;

    @Value("${mapbox.access.token}")
    private String mapboxAccessToken;

    @Value("${mapbox.user:entur}")
    private String mapboxUser;

    @Value("${mapbox.aws.region:us-east-1")
    private String awsRegion;

    @Value("${mapbox.upload.status.max.retries:10}")
    private int maxRetries;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("quartz2://marduk/mapboxUpdate?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{mapbox.update.autoStartup:false}}")
                .filter(e -> isLeader(e.getFromRouteId()))
                .log(LoggingLevel.INFO, "Update mapbox tileset from tiamat export started by cron")
                .to("direct:uploadTiamatToMapboxAsGeoJson")
                .routeId("mapbox-update-quartz");

        from("direct:uploadTiamatToMapboxAsGeoJson")
                .setHeader(TIAMAT_EXPORT_GCP_PATH, simple(blobStoreSubdirectoryForTiamatGeoCoderExport + "/" + TiamatGeoCoderExportRouteBuilder.TIAMAT_EXPORT_LATEST_FILE_NAME))
                .to("direct:recreateLocalMapboxDirectory")
                .to("direct:downloadLatestTiamatExportToMapboxFolder")
                .to("direct:mapboxUnzipLatestTiamatExportToFolder")
                .to("direct:retrieveMapboxAwsCredentials")
                .to("direct:findFirstXmlFileRecursive")
                .to("direct:transformToGeoJsonFromTiamat")
                .to("direct:uploadMapboxDataAws")
                .to("direct:initiateMapboxUpload")
                .delay(POLL_MAPBOX_UPLOAD_STATUS_DELAY)
                .to("direct:pollRetryMapboxStatus")
                .to("direct:recreateLocalMapboxDirectory")
                .log(LoggingLevel.INFO, "Finished inserting tiamat data")
                .routeId("mapbox-convert-tiamat-data");

        from("direct:initiateMapboxUpload")
                .process(exchange -> exchange.getOut().setBody(new MapboxUploadRequest(mapboxUser  + ".automated-uploaded-tileset", ((MapBoxAwsCredentials) exchange.getIn().getHeader("credentials")).getUrl(), exchange.getIn().getHeader("filename").toString())))
                .marshal().json(JsonLibrary.Jackson)
                .log(LoggingLevel.INFO, "${body}")
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
                .to(mapboxApiUrl + "/uploads/v1/" + mapboxUser + "?access_token=" + mapboxAccessToken)
                .unmarshal().json(JsonLibrary.Jackson, MapBoxUploadStatus.class)
                .log(LoggingLevel.INFO, "Received ${body}")
                .routeId("initiate-mapbox-upload");

        from("direct:uploadMapboxDataAws")
                .setHeader("filename", constant(TIAMAT_GEOSJON_FILENAME))
                .bean("awsS3Uploader", "upload")
                .routeId("upload-mapbox-data-aws");

        from("direct:findFirstXmlFileRecursive")
                .process(e -> e.getIn().setBody( FileUtils.listFiles(new File(localWorkingDirectory + "/tiamat"), new String[]{"xml"}, true).stream().findFirst().get()))
                .routeId("mapbox-find-first-xml-file-recursive");

        from("direct:pollRetryMapboxStatus")
                .loopDoWhile(simple("${body.error} == null && ${body.complete} == false"))
                .process(e -> e.getIn().setHeader(LOOP_COUNTER, (Integer) e.getIn().getHeader(LOOP_COUNTER, 0) + 1))
                .log("loop counter ${header." + LOOP_COUNTER + "}")
                .to("direct:endIfMapboxUploadError")

                .choice()
                    .when(simple("${body.complete}"))
                        .log("complete ${body}")
                        .to("direct:mapboxEnd")
                    .otherwise()
                        .log(LoggingLevel.INFO, "${body.id} Not complete yet.. wait a bit and try again")
                        .delay(POLL_MAPBOX_UPLOAD_STATUS_DELAY)
                        .to("direct:fetchMapboxUploadStatus")
                .endChoice()

                .choice()
                    .when(simple("${header." + LOOP_COUNTER + "} > " + maxRetries))
                        .log(LoggingLevel.WARN, getClass().getName(), "Giving up after looping after " + maxRetries + " iterations")
                        .stop() // end route?
                .endChoice()
                .routeId("mapbox-poll-retry-upload-status");

        from("direct:endIfMapboxUploadError")
                .choice()
                    .when(simple("${body.error}"))
                    .log(LoggingLevel.ERROR, "Got error uploading tileset. ${body}")
                    .stop()
                .endChoice();

        from("direct:fetchMapboxUploadStatus")
                .setProperty("tilesetId", simple("${body.id}"))
                .log("Tilset id is: ${property.tilesetId}")
                .setBody(simple(""))
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                .toD(mapboxApiUrl + "/uploads/v1/" + mapboxUser + "/${property.tilesetId}?access_token=" + mapboxAccessToken)
                .unmarshal().json(JsonLibrary.Jackson, MapBoxUploadStatus.class)
                .log("${body}")
                .routeId("fetch-mapbox-upload-status");

        from("direct:mapboxEnd")
                .log("COMPLETE")
                .end()
                .routeId("mapbox-upload-complete-end");

        from("direct:retrieveMapboxAwsCredentials")
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
                .to(mapboxApiUrl + "/uploads/v1/" + mapboxUser + "/credentials?access_token=" + mapboxAccessToken)
                .unmarshal().json(JsonLibrary.Jackson, MapBoxAwsCredentials.class)
                .setHeader("credentials", simple("${body}"))
                .log(LoggingLevel.INFO, "retrieved credentials: ${header.credentials}")
                .routeId("mapbox-retrieve-aws-credentials");

        from("direct:downloadLatestTiamatExportToMapboxFolder")
                .setHeader(FILE_HANDLE, header(TIAMAT_EXPORT_GCP_PATH))
                .to("direct:getBlob")
                .choice()
                .when(body().isNotEqualTo(null))
                .to("file:" + localWorkingDirectory + "/tiamat/?fileName=" + TiamatGeoCoderExportRouteBuilder.TIAMAT_EXPORT_LATEST_FILE_NAME)
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
                .routeId("mapbox-transform-from-tiamat");
    }
}

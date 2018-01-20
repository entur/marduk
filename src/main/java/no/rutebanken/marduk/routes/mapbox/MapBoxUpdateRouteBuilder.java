package no.rutebanken.marduk.routes.mapbox;


import com.google.common.base.Strings;
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
    private static final String TIAMAT_GEOSJON_FILENAME = "tiamat.geojson";

    protected static final String PROPERTY_STATE = "state";
    protected static final String STATE_FINISHED = "finished";
    protected static final String STATE_ERROR = "error";
    protected static final String STATE_TIMEOUT = "timeout";

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

    @Value("${blobstore.gcs.project.id:}")
    private String projectId;

    @Value("${mapbox.access.token:}")
    private String mapboxAccessToken;

    @Value("${mapbox.user:entur}")
    private String mapboxUser;

    @Value("${mapbox.aws.region:us-east-1")
    private String awsRegion;

    @Value("${mapbox.upload.status.max.retries:10}")
    private int mapboxUploadPollMaxRetries;

    @Value("${mapbox.upload.status.poll.delay:15000}")
    private int mapboxUploadPollDelay;

    @Override
    public void configure() throws Exception {
        super.configure();

        final String tilesetName = mapboxUser + ".automated-uploaded-tileset" + (Strings.isNullOrEmpty(projectId) ? "" : "-" + projectId);

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
                .setHeader("filename", constant(TIAMAT_GEOSJON_FILENAME))
                .to("direct:uploadMapboxDataAws")
                .to("direct:initiateMapboxUpload")
                .delay(mapboxUploadPollDelay)
                .to("direct:pollRetryMapboxStatus")
                .to("direct:recreateLocalMapboxDirectory")
                .routeId("mapbox-convert-upload-tiamat-data");

        from("direct:initiateMapboxUpload")
                .process(exchange -> exchange.getOut().setBody(
                        new MapboxUploadRequest(tilesetName,
                            ((MapBoxAwsCredentials) exchange.getIn().getHeader("credentials")).getUrl(),
                            exchange.getIn().getHeader("filename").toString())))
                .marshal().json(JsonLibrary.Jackson)
                .log(LoggingLevel.INFO, "Upload: ${body}")
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
                .to(mapboxApiUrl + "/uploads/v1/" + mapboxUser + "?access_token=" + mapboxAccessToken)
                .unmarshal().json(JsonLibrary.Jackson, MapBoxUploadStatus.class)
                .log(LoggingLevel.INFO, "Received ${body}")
                .routeId("initiate-mapbox-upload");

        from("direct:uploadMapboxDataAws")
                .bean("awsS3Uploader", "upload")
                .routeId("upload-mapbox-data-aws");

        from("direct:findFirstXmlFileRecursive")
                .process(e -> e.getIn().setBody( FileUtils.listFiles(new File(localWorkingDirectory + "/tiamat"), new String[]{"xml"}, true).stream().findFirst().get()))
                .routeId("mapbox-find-first-xml-file-recursive");

        from("direct:pollRetryMapboxStatus")
                .process(e -> e.getIn().setHeader(LOOP_COUNTER, 0))
                .loopDoWhile(simple("${header."+LOOP_COUNTER +"} <= " + mapboxUploadPollMaxRetries))
                .process(e -> e.getIn().setHeader(LOOP_COUNTER, (Integer) e.getIn().getHeader(LOOP_COUNTER, 0) + 1))
                .to("direct:endIfMapboxUploadError")

                .choice()
                    .when(simple("${body.complete}"))
                        .log(LoggingLevel.INFO,"Tileset upload complete: ${body.id}")
                        .setProperty(PROPERTY_STATE, simple(STATE_FINISHED))
                        .stop()
                    .otherwise()
                        .log(LoggingLevel.INFO, "Tileset upload ${body.id}: Not complete yet.. wait a bit and try again. (${header.\"" + LOOP_COUNTER + "\"})")
                        .delay(mapboxUploadPollDelay)
                        .to("direct:fetchMapboxUploadStatus")
                .endChoice()

                .choice()
                    .when(simple("${header." + LOOP_COUNTER + "} > " + mapboxUploadPollMaxRetries))
                        .log(LoggingLevel.WARN, getClass().getName(), "Giving up after looping after " + mapboxUploadPollMaxRetries + " iterations")
                .setProperty(PROPERTY_STATE, simple(STATE_TIMEOUT))
                        .stop() // end route?
                .endChoice()
                .routeId("mapbox-poll-retry-upload-status");

        from("direct:endIfMapboxUploadError")
                .choice()
                    .when(simple("${body.error}"))
                    .log(LoggingLevel.ERROR, "Got error uploading tileset. ${body}")
                    .setProperty(PROPERTY_STATE, simple(STATE_ERROR))
                    .stop()
                .endChoice()
                .routeId("mapbox-end-if-upload-error");

        from("direct:fetchMapboxUploadStatus")
                .setProperty("tilesetId", simple("${body.id}"))
                .log("Checking status for tileset: ${property.tilesetId}")
                .setBody(simple(""))
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                .toD(mapboxApiUrl + "/uploads/v1/" + mapboxUser + "/${property.tilesetId}?access_token=" + mapboxAccessToken)
                .unmarshal().json(JsonLibrary.Jackson, MapBoxUploadStatus.class)
                .log("Received status ${body}")
                .routeId("fetch-mapbox-upload-status");

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

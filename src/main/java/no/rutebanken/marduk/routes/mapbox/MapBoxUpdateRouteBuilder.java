package no.rutebanken.marduk.routes.mapbox;


import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import no.rutebanken.marduk.geocoder.routes.tiamat.TiamatGeoCoderExportRouteBuilder;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import no.rutebanken.marduk.routes.mapbox.model.MapBoxAwsCredentials;
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

    @Value("${mapbox.api.url:https4://api.mapbox.com}")
    private String mapboxApiUrl;

    @Value("${mapbox.access.token}")
    private String mapboxAccessToken;

    @Value("${mapbox.user:entur}")
    private String mapboxUser;

    @Value("${mapbox.aws.region:us-east-1")
    private String awsRegion;
    
    @Override
    public void configure() throws Exception {
        super.configure();


        singletonFrom("quartz2://marduk/mapBoxUpdate?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{mapbox.update.autoStartup:false}}")
                .filter(e -> isLeader(e.getFromRouteId()))
                .log(LoggingLevel.INFO, "Update mapbox tileset")
                .to("direct:downloadUnzipMapboxData")
                .routeId("mapbox-update-quartz");

        from("direct:uploadTiamatToMapboxAsGeoJson")
                .setHeader(TIAMAT_EXPORT_GCP_PATH, simple(blobStoreSubdirectoryForTiamatGeoCoderExport + "/" + TiamatGeoCoderExportRouteBuilder.TIAMAT_EXPORT_LATEST_FILE_NAME))
                .to("direct:recreateLocalMapboxDirectory")
                .to("direct:downloadLatestTiamatExportToFolder")
                .to("direct:mapboxUnzipLatestTiamatExportToFolder")
                .to("direct:retrieveMapboxAwsCredentials")
                .process(e -> e.getIn().setBody( FileUtils.listFiles(new File(localWorkingDirectory + "/tiamat"), new String[]{"xml"}, true).stream().findFirst().get()))
                .to("direct:transformToGeoJsonFromTiamat")
                .setHeader("filename", constant("tiamat.geojson"))
                .bean("awsS3Uploader", "upload")
                .process(exchange -> exchange.getOut().setBody(new MapboxUploadRequest(mapboxUser  + ".automated-uploaded-tileset", ((MapBoxAwsCredentials) exchange.getIn().getHeader("credentials")).getUrl(), exchange.getIn().getHeader("filename").toString())))
                .marshal().json(JsonLibrary.Jackson)
                .log(LoggingLevel.INFO, "${body}")
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
                .to(mapboxApiUrl + "/uploads/v1/" + mapboxUser + "?access_token=" + mapboxAccessToken)
                .log(LoggingLevel.INFO, "${body}")
                .log(LoggingLevel.INFO, "Finished inserting tiamat data")
                .routeId("mapbox-convert-tiamat-data");


        from("direct:retrieveMapboxAwsCredentials")
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
                .to(mapboxApiUrl + "/uploads/v1/" + mapboxUser + "/credentials?access_token="+mapboxAccessToken)
                .unmarshal().json(JsonLibrary.Jackson, MapBoxAwsCredentials.class)
                .setHeader("credentials", simple("${body}"))
                .log(LoggingLevel.INFO, "retrieved credentials: ${header.credentials}")
                .routeId("mapbox-retrieve-aws-credentials");


        from("direct:downloadLatestTiamatExportToFolder")
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
                .routeId("mapbox-convert-from-tiamat");


    }

}

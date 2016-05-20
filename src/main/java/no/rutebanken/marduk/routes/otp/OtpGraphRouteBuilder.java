package no.rutebanken.marduk.routes.otp;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.util.stream.Collectors;

import static no.rutebanken.marduk.Constants.*;
import static org.apache.commons.io.FileUtils.deleteDirectory;

/**
 * Trigger OTP graph building
 */
@Component
public class OtpGraphRouteBuilder extends BaseRouteBuilder {

    private static final String BUILD_CONFIG_JSON = "build-config.json";
    private static final String NORWAY_LATEST_OSM_PBF = "norway-latest.osm.pbf";
    private static final String GRAPH_OBJ = "Graph.obj";
    private static final String TIMESTAMP = "RutebankenTimeStamp";

    @Value("${otp.graph.build.directory}")
    private String otpGraphBuildDirectory;

    @Value("${otp.graph.map.base.url}")
    private String mapBaseUrl;

    @Value("${otp.graph.blobstore.subdirectory}")
    private String blobStoreSubdirectory;

    @Override
    public void configure() throws Exception {
        super.configure();

        //TODO Report status?
        from("activemq:queue:OtpGraphQueue?maxConcurrentConsumers=1")
                .setProperty(TIMESTAMP, simple("${date:now:yyyyMMddHHmmss}"))
                .setProperty(OTP_GRAPH_DIR, simple(otpGraphBuildDirectory + "/${property." + TIMESTAMP + "}"))
                .log(LoggingLevel.INFO, getClass().getName(), "Starting graph building in directory ${property." + OTP_GRAPH_DIR + "}.")
                .to("direct:fetchLatestGtfs")
                .to("direct:fetchConfig")
                .to("direct:fetchMap")
                .to("direct:buildGraph")
                .log(LoggingLevel.INFO, getClass().getName(), "Done with OTP graph building route.");

        from("direct:fetchLatestGtfs")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching gtfs files for all providers.")
                .setBody(simple(getAggregatedGtfsFiles()))
                .split(body())
                .to("direct:getGtfsFiles");

        from("direct:getGtfsFiles")
            .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching outbound/gtfs/${body}")
                .setProperty("fileName", body())
                .setHeader(FILE_HANDLE, simple("outbound/gtfs/${property.fileName}"))
                .to("direct:getBlob")
                .choice()
                .when(body().isNotEqualTo(null))
                .toD("file:" + otpGraphBuildDirectory + "?fileName=${property." + TIMESTAMP +"}/${property.fileName}")
                .otherwise()
                .log(LoggingLevel.INFO, getClass().getName(), "${property.fileName} was empty when trying to fetch it from blobstore.");

        from("direct:fetchConfig")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching config ...")
                .to("language:constant:resource:classpath:no/rutebanken/marduk/routes/otp/" + BUILD_CONFIG_JSON)
                .toD("file:" + otpGraphBuildDirectory + "?fileName=${property." + TIMESTAMP +"}/" + BUILD_CONFIG_JSON)
                .log(LoggingLevel.DEBUG, getClass().getName(), BUILD_CONFIG_JSON + " fetched.");

        from("direct:fetchMap")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching map ...")
                .removeHeaders("*")
                .setBody(constant(""))
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                .to(mapBaseUrl + "/" + NORWAY_LATEST_OSM_PBF)
                .toD("file:" + otpGraphBuildDirectory + "?fileName=${property." + TIMESTAMP +"}/" + NORWAY_LATEST_OSM_PBF)
                .log(LoggingLevel.DEBUG, getClass().getName(), NORWAY_LATEST_OSM_PBF + " fetched.");

        from("direct:buildGraph")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Building graph ...")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Building OTP graph...")
                .process(new GraphBuilderProcessor())
                .setBody(constant(""))
                .toD("file:" + otpGraphBuildDirectory + "?fileName=${property." + TIMESTAMP +"}/" + GRAPH_OBJ + ".done")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Done building new OTP graph.");

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

        from("direct:cleanUp")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Deleting build folder ${property." + Exchange.FILE_PARENT + "} ...")
                .process(e -> deleteDirectory(new File(e.getIn().getExchange().getProperty(Exchange.FILE_PARENT, String.class))))
                .log(LoggingLevel.DEBUG, getClass().getName(), "Build folder ${property." + Exchange.FILE_PARENT + "} cleanup done.");

    }

    String getAggregatedGtfsFiles(){
        return getProviderRepository().getProviders().stream()
                .map(p -> p.id + "-" + CURRENT_AGGREGATED_GTFS_FILENAME).collect(Collectors.joining(","));
    }

}

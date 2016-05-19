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
import static org.apache.commons.io.FileUtils.cleanDirectory;

/**
 * Trigger OTP graph building
 */
@Component
public class OtpGraphRouteBuilder extends BaseRouteBuilder {

    private static final String BUILD_CONFIG_JSON = "build-config.json";
    private static final String NORWAY_LATEST_OSM_PBF = "norway-latest.osm.pbf";
    private static final String GRAPH_OBJ = "Graph.obj";

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
                .log(LoggingLevel.INFO, getClass().getName(), "Starting graph building.")
                .to("direct:cleanUp")
                .to("direct:fetchLatestGtfs")
                .to("direct:fetchConfig")
//                .to("direct:fetchMap")
                .to("direct:buildGraph")
                .to("direct:publishGraph");

        from("direct:cleanUp")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Cleaning build folder ...")
                .process(e -> cleanDirectory(new File(otpGraphBuildDirectory)))
                .log(LoggingLevel.DEBUG, getClass().getName(), "Build folder cleanup done.");

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
                .toD("file://" + otpGraphBuildDirectory + "?fileName=${property.fileName}")
                .otherwise()
                .log(LoggingLevel.INFO, getClass().getName(), "${property.fileName} was empty when trying to fetch it from blobstore.");

        from("direct:fetchConfig")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching config ...")
                .to("language:constant:resource:classpath:no/rutebanken/marduk/routes/otp/" + BUILD_CONFIG_JSON)
                .to("file://" + otpGraphBuildDirectory + "?fileName=" + BUILD_CONFIG_JSON)
                .log(LoggingLevel.DEBUG, getClass().getName(), BUILD_CONFIG_JSON + " fetched.");

        from("direct:fetchMap")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching map ...")
                .removeHeaders("*")
                .setBody(constant(""))
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                .to(mapBaseUrl + "/" + NORWAY_LATEST_OSM_PBF)
                .to("file://" + otpGraphBuildDirectory + "?fileName=" + NORWAY_LATEST_OSM_PBF)
                .log(LoggingLevel.DEBUG, getClass().getName(), NORWAY_LATEST_OSM_PBF + " fetched.");

        from("direct:buildGraph")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Building graph ...")
                .setProperty(OTP_GRAPH_DIR, constant(otpGraphBuildDirectory))
                .log(LoggingLevel.DEBUG, getClass().getName(), "Directory for graph building is ${property." + OTP_GRAPH_DIR + "}")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Building OTP graph...")
                .process(new GraphBuilderProcessor())
                .setBody(constant(""))
                .to("file:" + otpGraphBuildDirectory + "?fileName=" + GRAPH_OBJ + ".done")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Done building new OTP graph.");

        from("file:" + otpGraphBuildDirectory + "?fileName=" + GRAPH_OBJ + "&doneFileName=" + GRAPH_OBJ + ".done")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .convertBodyTo(InputStream.class)
                .setHeader(FILE_HANDLE, simple(blobStoreSubdirectory + "/${date:now:yyyyMMddHHmmss}-" + GRAPH_OBJ))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .to("direct:uploadBlob")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Done uploading new OTP graph.")
                .to("direct:notify")
                .log(LoggingLevel.INFO, getClass().getName(), "Done with OTP graph building route.");

    }

    String getAggregatedGtfsFiles(){
        return getProviderRepository().getProviders().stream()
                .map(p -> p.id + "-" + CURRENT_AGGREGATED_GTFS_FILENAME).collect(Collectors.joining(","));
    }

}

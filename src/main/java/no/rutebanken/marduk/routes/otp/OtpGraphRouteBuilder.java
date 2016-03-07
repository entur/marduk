package no.rutebanken.marduk.routes.otp;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;

import static org.apache.commons.io.FileUtils.deleteDirectory;
import static no.rutebanken.marduk.Constants.*;

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
        from("activemq:queue:OtpGraphQueue")
                .log(LoggingLevel.INFO, getClass().getName(), "Starting graph building.")
                .to("direct:fetchLatestGtfs")
                .to("direct:fetchConfig")
                .to("direct:fetchMap")
                .to("direct:buildGraph")
                .to("direct:uploadGraph")
                .to("direct:notify")
                .log(LoggingLevel.INFO, getClass().getName(), "Done with graph building.");

        from("direct:fetchLatestGtfs")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching gtfs ...")
                .setHeader(FILE_HANDLE, constant("outbound/gtfs/" + CURRENT_AGGREGATED_GTFS_FILENAME))
                .to("direct:getBlob")
                .to("file://" + otpGraphBuildDirectory + "?fileName=" + CURRENT_AGGREGATED_GTFS_FILENAME)
                .log(LoggingLevel.DEBUG, getClass().getName(), "Gtfs fetched.");

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
                .log(LoggingLevel.DEBUG, getClass().getName(), "Done building new OTP graph.");

        from("direct:uploadGraph")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Uploading new OTP graph ...")
                .setBody(constant(""))
                .removeHeaders("*")
                .to("file:"+ otpGraphBuildDirectory + "?fileName=" + GRAPH_OBJ + "&delete=true")
                .setHeader(FILE_HANDLE, simple(blobStoreSubdirectory + "/${date:now:yyyyMMddHHmmss}-" + GRAPH_OBJ))
                .to("direct:uploadBlob")
                .process(e -> deleteDirectory(new File(otpGraphBuildDirectory)))
                .log(LoggingLevel.DEBUG, getClass().getName(), "Done uploading new OTP graph.");

    }

}

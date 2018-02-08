/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.routes.otp;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.List;

import static no.rutebanken.marduk.Constants.*;
import static org.apache.camel.Exchange.FILE_PARENT;
import static org.apache.camel.builder.Builder.exceptionStackTrace;

/**
 * Prepare half baked otp base graph object, containing all data except transit data (osm, height)
 */
@Component
public class OtpBaseGraphRouteBuilder extends BaseRouteBuilder {

    private static final String BUILD_CONFIG_JSON = "build-config.json";
    private static final String NORWAY_LATEST_OSM_PBF = "norway-latest.osm.pbf";

    @Value("${otp.base.graph.build.directory:files/otpgraph/base}")
    private String otpBaseGraphBuildDirectory;

    @Value("${otp.graph.osm.file.name:norway-latest.osm.pbf}")
    private String osmNorwayMapFileName;

    @Value("#{'${otp.graph.additional.files.subdirectory.names:osm/static,kartverket/heightData}'.split(',')}")
    private List<String> additionalFilesSubDirectories;

    @Value("${otp.graph.blobstore.subdirectory:graphs}")
    private String blobStoreSubdirectory;

    @Value("${otp.base.graph.build.config:}")
    private String otpGraphBuildConfig;

    @Value("${osm.pbf.blobstore.subdirectory:osm}")
    private String blobStoreSubdirectoryForOsm;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("activemq:queue:OtpBaseGraphBuildQueue?transacted=true&maxConcurrentConsumers=1&messageListenerContainerFactoryRef=batchListenerContainerFactory").autoStartup("{{otp.base.graph.build.autoStartup:true}}")
                .transacted()
                .doTry() // <- doTry seems necessary for correct transactional handling. not sure why...
                .setProperty(TIMESTAMP, simple("${date:now:yyyyMMddHHmmss}"))
                .to("direct:sendOtpBaseGraphStartedEventsInNewTransaction")
                .setProperty(OTP_GRAPH_DIR, simple(otpBaseGraphBuildDirectory + "/${property." + TIMESTAMP + "}"))
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Starting OTP base graph building in directory ${property." + OTP_GRAPH_DIR + "}.")
                .to("direct:fetchAdditionalMapDataForOtpGraphBaseBuild")
                .to("direct:fetchBuildConfigForOtpGraphBaseBuild")
                .to("direct:fetchNorwayMapForOtpGraphBaseBuild")
                .to("direct:buildBaseGraphAndSendStatus")
                .setHeader(FILE_PARENT, exchangeProperty(OTP_GRAPH_DIR))
                .to("direct:cleanUpLocalDirectory")

                .log(LoggingLevel.INFO, getClass().getName(), "Done with OTP base graph building route.")
                .routeId("otp-base-graph-build");

        from("direct:sendOtpBaseGraphStartedEventsInNewTransaction")
                .transacted("PROPAGATION_REQUIRES_NEW")
                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.GRAPH).action("BUILD_BASE").state(JobEvent.State.STARTED).correlationId(e.getProperty(TIMESTAMP, String.class)).build()).to("direct:updateStatus")
                .routeId("otp-base-graph-build-send-started-events");

        from("direct:fetchBuildConfigForOtpGraphBaseBuild")
                .choice().when(constant(StringUtils.isEmpty(otpGraphBuildConfig)))
                .log(LoggingLevel.DEBUG, getClass().getName(), correlation() + "Fetching config ...")
                .to("language:constant:resource:classpath:no/rutebanken/marduk/routes/otp/" + BUILD_CONFIG_JSON)
                .otherwise()
                .log(LoggingLevel.WARN, getClass().getName(), correlation() + "Using overridden otp build config from property")
                .setBody(constant(otpGraphBuildConfig))
                .end()
                .toD("file:" + otpBaseGraphBuildDirectory + "?fileName=${property." + TIMESTAMP + "}/" + BUILD_CONFIG_JSON)
                .log(LoggingLevel.DEBUG, getClass().getName(), correlation() + BUILD_CONFIG_JSON + " fetched.")
                .routeId("otp-base-graph-build-fetch-config");

        from("direct:fetchNorwayMapForOtpGraphBaseBuild")
                .log(LoggingLevel.DEBUG, getClass().getName(), correlation() + "Fetching map ...")
                .setHeader(FILE_HANDLE, simple(blobStoreSubdirectoryForOsm + "/" + osmNorwayMapFileName))
                .to("direct:getBlob")
                // Should really store to osmNorwayMapFileName, but store to NORWAY_LATEST in fear of side effects later in the build
                .toD("file:" + otpBaseGraphBuildDirectory + "?fileName=${property." + TIMESTAMP + "}/" + NORWAY_LATEST_OSM_PBF)
                .log(LoggingLevel.DEBUG, getClass().getName(), correlation() + NORWAY_LATEST_OSM_PBF + " fetched (original name: " + osmNorwayMapFileName + ").")
                .routeId("otp-base-graph-build-fetch-map");

        from("direct:fetchAdditionalMapDataForOtpGraphBaseBuild")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Fetching additional files for map building from: " + additionalFilesSubDirectories)
                .setHeader(FILE_PARENT_COLLECTION, constant(additionalFilesSubDirectories))
                .to("direct:listBlobsInFolders")
                .split().simple("${body.files}")
                .setProperty("tmpFileName", simple("${body.fileNameOnly}"))
                .filter(simple("${body.fileNameOnly}"))
                .setHeader(FILE_HANDLE, simple("${body.name}"))
                .to("direct:getBlob")
                .toD("file:" + otpBaseGraphBuildDirectory + "?fileName=${property." + TIMESTAMP + "}/${exchangeProperty.tmpFileName}")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Fetched additional map file:${header." + FILE_HANDLE + "}")
                .routeId("otp-base-graph-build-fetch-map-additional");


        from("direct:buildBaseGraphAndSendStatus")
                .log(LoggingLevel.INFO, correlation() + "Preparing OTP graph with all non-transit data...")
                .doTry()
                .to("direct:buildOtpBaseGraph")
                .doCatch(Exception.class)
                .log(LoggingLevel.ERROR, correlation() + "Graph building failed: " + exceptionMessage() + " stacktrace: " + exceptionStackTrace())
                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.GRAPH).action("BUILD_BASE").state(JobEvent.State.FAILED).correlationId(e.getProperty(TIMESTAMP, String.class)).build()).to("direct:updateStatus")
                .end()
                .routeId("otp-base-graph-build-build-and-send-status");

        from("direct:buildOtpBaseGraph")
                .process(new GraphBuilderProcessor(true))
                .log(LoggingLevel.INFO, correlation() + "Done building new OTP base graph.")
                .process(e -> e.getIn().setBody(new File(e.getProperty(OTP_GRAPH_DIR) + "/" + BASE_GRAPH_OBJ)))
                .setHeader(FILE_HANDLE, constant(blobStoreSubdirectory + "/" + BASE_GRAPH_OBJ))
                .to("direct:uploadBlob")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.INFO, correlation() + "Uploaded new new OTP base graph, triggering full OTP graph build")
                .inOnly("activemq:queue:OtpNetexGraphQueue")
                .routeId("otp-base-graph-build-build-otp");

    }
}

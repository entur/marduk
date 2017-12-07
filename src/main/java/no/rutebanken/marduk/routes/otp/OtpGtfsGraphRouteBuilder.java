package no.rutebanken.marduk.routes.otp;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.GtfsFileUtils;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.activemq.command.ActiveMQMessage;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import static no.rutebanken.marduk.Constants.*;
import static org.apache.camel.builder.Builder.exceptionStackTrace;

/**
 * Trigger OTP graph building
 */
@Component
public class OtpGtfsGraphRouteBuilder extends BaseRouteBuilder {

    private static final String BUILD_CONFIG_JSON = "build-config.json";
    private static final String NORWAY_LATEST_OSM_PBF = "norway-latest.osm.pbf";

    @Value("${otp.graph.build.directory}")
    private String otpGraphBuildDirectory;

    /**
     * This is the name which the graph file is stored remotely.
     */
    @Value("${otp.graph.file.name:norway-latest.osm.pbf}")
    private String otpGraphFileName;

    @Value("${otp.graph.blobstore.subdirectory}")
    private String blobStoreSubdirectory;

    @Value("${otp.graph.build.config:}")
    private String otpGraphBuildConfig;

    @Value("${osm.pbf.blobstore.subdirectory:osm}")
    private String blobStoreSubdirectoryForOsm;

    @Value("${gtfs.norway.merged.file.name:rb_norway-aggregated-gtfs.zip}")
    private String gtfsNorwayMergedFileName;

    private static final String PROP_MESSAGES = "RutebankenPropMessages";

    private static final String HEADER_STATUS = "RutebankenGraphBuildStatus";

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("activemq:queue:OtpGtfsGraphQueue?transacted=true&maxConcurrentConsumers=1&messageListenerContainerFactoryRef=batchListenerContainerFactory").autoStartup("{{otp.graph.build.autoStartup:true}}")
                .transacted()
                .doTry() // <- doTry seems necessary for correct transactional handling. not sure why...
                .setProperty(PROP_MESSAGES, simple("${body}"))
                .setProperty(TIMESTAMP, simple("${date:now:yyyyMMddHHmmss}"))
                .to("direct:sendStartedEventsInNewTransaction")
                .setProperty(OTP_GRAPH_DIR, simple(otpGraphBuildDirectory + "/${property." + TIMESTAMP + "}"))
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Starting graph building in directory ${property." + OTP_GRAPH_DIR + "}.")
                .to("direct:fetchConfig")
                .to("direct:fetchMap")

                .to("direct:getLatestMergedGtfs")
                .to("direct:transformToOTPIds")
                .to("direct:buildGraphAndSendStatus")
                .log(LoggingLevel.INFO, getClass().getName(), "Done with OTP graph building route.")
                .routeId("otp-graph-build");

        from("direct:sendStartedEventsInNewTransaction")
                .transacted("PROPAGATION_REQUIRES_NEW")
                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.GRAPH).action("BUILD_GRAPH").state(JobEvent.State.STARTED).correlationId(e.getProperty(TIMESTAMP, String.class)).build()).to("direct:updateStatus")
                .setHeader(HEADER_STATUS, constant(JobEvent.State.STARTED))
                .to("direct:sendStatusForJobs")
                .routeId("otp-graph-send-started-events");

        from("direct:sendStatusForJobs")
                .doTry() // <- doTry seems necessary for correct transactional handling. not sure why...
                .split().exchangeProperty(PROP_MESSAGES)
                .filter(simple("${body.properties[" + CHOUETTE_REFERENTIAL + "]}"))
                .process(e -> {
                    JobEvent.State state = e.getIn().getHeader(HEADER_STATUS, JobEvent.State.class);
                    e.getIn().setHeaders(((ActiveMQMessage) e.getIn().getBody()).getProperties());
                    JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.BUILD_GRAPH).state(state).build();
                })
                .to("direct:updateStatus")
                .routeId("otp-graph-send-status-for-timetable-jobs");


        from("direct:getLatestMergedGtfs")
                .setHeader(FILE_HANDLE, simple(BLOBSTORE_PATH_OUTBOUND + gtfsNorwayMergedFileName))
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Fetching latest GTFS file for norway: " + " ${header." + FILE_HANDLE + "}")
                .to("direct:getBlob")
                .choice()
                .when(body().isNotEqualTo(null))
                .toD("file:" + otpGraphBuildDirectory + "?fileName=${property." + TIMESTAMP + "}/org/merged.zip")
                .otherwise()
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "${property.fileName} was empty when trying to fetch it from blobstore.")
                .routeId("otp-graph-get-merged-gtfs");

        from("direct:transformToOTPIds")
                .setBody(simple(otpGraphBuildDirectory + "/${property." + TIMESTAMP + "}/org/merged.zip"))
                .log(LoggingLevel.DEBUG, getClass().getName(), "Replacing id separator in merged GTFS file to suit OTP")
                .bean(method(GtfsFileUtils.class, "transformIdsToOTPFormat"))
                .toD("file:" + otpGraphBuildDirectory + "?fileName=${property." + TIMESTAMP + "}/merged.zip")
                .routeId("otp-graph-transform-ids");

        from("direct:fetchConfig")
                .choice().when(constant(StringUtils.isEmpty(otpGraphBuildConfig)))
                .log(LoggingLevel.DEBUG, getClass().getName(), correlation() + "Fetching config ...")
                .to("language:constant:resource:classpath:no/rutebanken/marduk/routes/otp/" + BUILD_CONFIG_JSON)
                .otherwise()
                .log(LoggingLevel.WARN, getClass().getName(), correlation() + "Using overridden otp build config from property")
                .setBody(constant(otpGraphBuildConfig))
                .end()
                .toD("file:" + otpGraphBuildDirectory + "?fileName=${property." + TIMESTAMP + "}/" + BUILD_CONFIG_JSON)
                .log(LoggingLevel.DEBUG, getClass().getName(), correlation() + BUILD_CONFIG_JSON + " fetched.")
                .routeId("otp-graph-fetch-config");

        from("direct:fetchMap")
                .log(LoggingLevel.DEBUG, getClass().getName(), correlation() + "Fetching map ...")
                .setHeader(FILE_HANDLE, simple(blobStoreSubdirectoryForOsm + "/" + otpGraphFileName))
                .to("direct:getBlob")
                // Should really store to otpGraphFileName, but store to NORWAY_LATEST in fear of side effects later in the build
                .toD("file:" + otpGraphBuildDirectory + "?fileName=${property." + TIMESTAMP + "}/" + NORWAY_LATEST_OSM_PBF)
                .log(LoggingLevel.DEBUG, getClass().getName(), correlation() + NORWAY_LATEST_OSM_PBF + " fetched (original name: " + otpGraphFileName + ").")
                .routeId("otp-graph-fetch-map");


        from("direct:buildGraphAndSendStatus")
                .log(LoggingLevel.INFO, correlation() + "Building OTP graph...")
                .doTry()
                .to("direct:buildGraph")
                .setHeader(HEADER_STATUS, constant(JobEvent.State.OK))
                .to("direct:sendStatusForJobs")
                .doCatch(Exception.class)
                .log(LoggingLevel.ERROR, correlation() + "Graph building failed: " + exceptionMessage() + " stacktrace: " + exceptionStackTrace())
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.FAILED).build()).to("direct:updateStatus")
                .setHeader(HEADER_STATUS, constant(JobEvent.State.FAILED))
                .to("direct:sendStatusForJobs")
                .end()
                .routeId("otp-graph-build-and-send-status");

        from("direct:buildGraph")
                .process(new GraphBuilderProcessor())
                .setBody(constant(""))
                .toD("file:" + otpGraphBuildDirectory + "?fileName=${property." + TIMESTAMP + "}/" + GRAPH_OBJ + ".done")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.INFO, correlation() + "Done building new OTP graph.")
                .routeId("otp-graph-build-otp");

    }


}

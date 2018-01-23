package no.rutebanken.marduk.geocoder.routes.pelias;


import no.rutebanken.marduk.geocoder.GeoCoderConstants;
import no.rutebanken.marduk.geocoder.routes.control.GeoCoderTaskType;
import no.rutebanken.marduk.geocoder.routes.pelias.babylon.DeploymentStatus;
import no.rutebanken.marduk.geocoder.routes.pelias.babylon.ScalingOrder;
import no.rutebanken.marduk.geocoder.routes.pelias.babylon.StartFile;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.JOB_STATUS_ROUTING_DESTINATION;
import static no.rutebanken.marduk.geocoder.GeoCoderConstants.*;

@Component
public class PeliasUpdateRouteBuilder extends BaseRouteBuilder {

    /**
     * One time per 24H on MON-FRI
     */
    @Value("${pelias.update.cron.schedule:0+0+23+?+*+MON-FRI}")
    private String cronSchedule;

    @Value("${babylon.url:http4://babylon/services/local}")
    private String babylonUrl;

    @Value("${elasticsearch.scratch.deployment.name:es-scratch}")
    private String elasticsearchScratchDeploymentName;

    @Value("${elasticsearch.build.file.name:es-image-build-pod.yaml}")
    private String elasticsearchBuildFileName;

    @Value("${elasticsearch.build.job.name:es-build-job}")
    private String elasticsearchBuildJobName;

    @Value("${tiamat.max.retries:3000}")
    private int maxRetries;

    @Value("${tiamat.retry.delay:15000}")
    private long retryDelay;

    // Whether or not to ask babylon to start a new es-scratch instance. Should only be set to false for local testing.
    @Value("${elasticsearch.scratch.start.new:true}")
    private boolean startNewEsScratch;

    @Autowired
    private PeliasUpdateStatusService updateStatusService;

    private static String NO_OF_REPLICAS = "RutebankenESNoOfReplicas";

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("quartz2://marduk/peliasUpdate?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{pelias.update.autoStartup:false}}")
                .filter(e -> isSingletonRouteActive(e.getFromRouteId()))
                .log(LoggingLevel.INFO, "Quartz triggers download of administrative units.")
                .setBody(constant(PELIAS_UPDATE_START))
                .to("direct:geoCoderStart")
                .routeId("pelias-update-quartz");

        from(PELIAS_UPDATE_START.getEndpoint())
                .log(LoggingLevel.INFO, "Start updating Pelias")
                .bean(updateStatusService, "setBuilding")
                .process(e -> JobEvent.systemJobBuilder(e).startGeocoder(GeoCoderTaskType.PELIAS_UPDATE).build()).to("direct:updateStatus")

                .choice()
                .when(constant(startNewEsScratch))
                .to("direct:startElasticsearchScratchInstance")
                .otherwise()
                .log(LoggingLevel.WARN,"Updating an existing es-scratch instance. Only for local testing!")
                .to("direct:insertElasticsearchIndexData")
                .routeId("pelias-upload");

        from("direct:startElasticsearchScratchInstance")
                .to("direct:getElasticsearchScratchStatus")

                .choice()
                .when(simple("${body.availableReplicas} > 0"))
                // Shutdown if already running
                .log(LoggingLevel.INFO, "Elasticsearch scratch instance already running. Scaling down first.")
                .setHeader(JOB_STATUS_ROUTING_DESTINATION, constant("direct:startElasticsearchScratchInstance"))
                .to("direct:shutdownElasticsearchScratchInstance")
                .otherwise()
                .setHeader(NO_OF_REPLICAS, constant(1))
                .setHeader(JOB_STATUS_ROUTING_DESTINATION, constant("direct:insertElasticsearchIndexData"))
                .to("direct:rescaleElasticsearchScratchInstance")
                .end()

                .routeId("pelias-es-scratch-start");


        from("direct:insertElasticsearchIndexDataCompleted")
                .setHeader(JOB_STATUS_ROUTING_DESTINATION, constant("direct:buildElasticsearchImage"))
                .setProperty(GEOCODER_NEXT_TASK, constant(GeoCoderConstants.PELIAS_ES_SCRATCH_STOP))
                .routeId("pelias-es-index-complete");


        from("direct:insertElasticsearchIndexDataFailed")
                .bean(updateStatusService, "setIdle")
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.FAILED).build()).to("direct:updateStatus")
                .routeId("pelias-es-index-failed");

        from("direct:shutdownElasticsearchScratchInstance")
                .setHeader(NO_OF_REPLICAS, constant(0))
                .to("direct:rescaleElasticsearchScratchInstance")
                .routeId("pelias-es-scratch-shutdown");


        from("direct:rescaleElasticsearchScratchInstance")
                .log(LoggingLevel.INFO, "Requesting Babylon to scale Elasticsearch scratch to ${header." + NO_OF_REPLICAS + "} replicas")
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .process(e -> e.getIn().setBody(new ScalingOrder(elasticsearchScratchDeploymentName, "marduk", e.getIn().getHeader(NO_OF_REPLICAS, Integer.class))))
                .marshal().json(JsonLibrary.Jackson)
                .to(babylonUrl + "/deployment/scale")
                .setProperty(GEOCODER_NEXT_TASK, constant(GeoCoderConstants.PELIAS_ES_SCRATCH_STATUS_POLL))
                .routeId("pelias-es-scratch-rescale");

        from("direct:pollElasticsearchScratchStatus")
                .to("direct:getElasticsearchScratchStatus")
                .choice()
                .when(simple("${body.availableReplicas} == ${header." + NO_OF_REPLICAS + "}"))
                .toD("${header." + JOB_STATUS_ROUTING_DESTINATION + "}")
                .otherwise()
                .setProperty(GEOCODER_RESCHEDULE_TASK, constant(true))
                .end()
                .routeId("pelias-es-scratch-status-poll");

        from("direct:getElasticsearchScratchStatus")
                .setBody(constant(null))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .to(babylonUrl + "/deployment/status?deployment=" + elasticsearchScratchDeploymentName)
                .unmarshal().json(JsonLibrary.Jackson, DeploymentStatus.class)
                .routeId("pelias-es-scratch-status");


        from("direct:buildElasticsearchImage")
                .log(LoggingLevel.INFO, "Requesting Babylon to build new elasticsearch image for pelias")
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .setBody(constant(new StartFile(elasticsearchBuildFileName)))
                .marshal().json(JsonLibrary.Jackson)
                .to(babylonUrl + "/job/run")
                .to("direct:processPeliasDeployCompleted")
                .routeId("pelias-es-build");

        from("direct:processPeliasDeployCompleted")
                .log(LoggingLevel.INFO, "Finished updating pelias")
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.OK).build()).to("direct:updateStatus")
                .bean(updateStatusService, "setIdle")
                .routeId("pelias-deploy-completed");


    }


}

package no.rutebanken.marduk.geocoder.routes.tiamat;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.geocoder.routes.tiamat.model.TiamatExportTask;
import no.rutebanken.marduk.geocoder.routes.tiamat.model.TiamatExportTasks;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.activemq.ScheduledMessage;
import org.apache.camel.LoggingLevel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.geocoder.GeoCoderConstants.GEOCODER_RESCHEDULE_TASK;
import static no.rutebanken.marduk.geocoder.routes.tiamat.model.TiamatExportTaskType.CHANGE_LOG;

/**
 * Routes for triggering regular exports of different datasets for backup and publish.
 * <p>
 * A list of conf
 */
@Component
public class TiamatPublishExportsRouteBuilder extends BaseRouteBuilder {


    @Value("${tiamat.publish.export.cron.schedule:0+0+23+?+*+MON-FRI}")
    private String cronSchedule;

    @Value("${tiamat.publish.export.blobstore.subdirectory:tiamat}")
    private String blobStoreSubdirectoryForTiamatExport;

    @Value("#{'${tiamat.publish.export:}'.split(';')}")
    private List<String> exportConfigStrings;

    @Value("${tiamat.export.max.retries:3000}")
    private int maxRetries;

    @Value("${tiamat.export.retry.delay:15000}")
    private long retryDelay;


    @Override
    public void configure() throws Exception {
        super.configure();

        List<TiamatExportTask> exportTasks =
                exportConfigStrings.stream().filter(s -> !StringUtils.isEmpty(s)).map(configStr -> new TiamatExportTask(configStr)).collect(Collectors.toList());


        singletonFrom("quartz2://marduk/tiamatPublishExport?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{tiamat.export.autoStartup:true}}")
                .filter(e -> isSingletonRouteActive(e.getFromRouteId()))
                .log(LoggingLevel.INFO, "Quartz triggers Tiamat exports for publish ")
                .to("direct:startFullTiamatPublishExport")
                .routeId("tiamat-publish-export-quartz");

        from("direct:startFullTiamatPublishExport")
                .choice()
                .when(constant(exportTasks.isEmpty()))
                .log(LoggingLevel.INFO, "Do nothing as no Tiamat publish tasks have been configured")
                .otherwise()
                .setBody(constant(new TiamatExportTasks(exportTasks).toString()))
                .log(LoggingLevel.INFO, "Starting Tiamat exports: ${body}")
                .inOnly("activemq:queue:TiamatExportQueue")
                .end()
                .routeId("tiamat-publish-export-start-full");


        singletonFrom("activemq:queue:TiamatExportQueue?transacted=true")
                .transacted()
                .process(e -> e.setProperty(TIAMAT_EXPORT_TASKS, TiamatExportTasks.fromString(e.getIn().getBody(String.class))))
                .process(e -> e.getIn().setHeader(LOOP_COUNTER, (Integer) e.getIn().getHeader(LOOP_COUNTER, 0) + 1))
                .setBody(constant(null))
                .choice()
                .when(simple("${header." + LOOP_COUNTER + "} == 1"))
                .to("direct:startNewTiamatExport")
                .otherwise()
                .to("direct:pollForTiamatExportStatus")
                .end()

                .choice()
                .when(simple("${exchangeProperty." + TIAMAT_EXPORT_TASKS + ".complete} == false"))
                .process(e -> e.getIn().setBody(e.getProperty(TIAMAT_EXPORT_TASKS, TiamatExportTasks.class).toString()))
                .to("activemq:TiamatExportQueue")
                .end()

                .routeId("tiamat-publish-export");


        from("direct:startNewTiamatExport")
                .process(e -> {
                    String name = e.getProperty(TIAMAT_EXPORT_TASKS, TiamatExportTasks.class).getCurrentTask().name;
                    JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.TIAMAT).action("EXPORT").fileName(name).state(JobEvent.State.STARTED).newCorrelationId().build();
                }).to("direct:updateStatus")
                .log(LoggingLevel.INFO, "Start Tiamat publish export: ${exchangeProperty." + TIAMAT_EXPORT_TASKS + ".currentTask.name}")

                .choice()
                .when(simple("${exchangeProperty." + TIAMAT_EXPORT_TASKS + ".currentTask.type.name} == '" + CHANGE_LOG.name() + "'"))
                .to("direct:processTiamatChangeLogExportTask")
                .to("direct:removeCurrentTask")
                .otherwise()
                .setHeader(Constants.JOB_STATUS_ROUTING_DESTINATION, constant("direct:processTiamatPublishExportResults"))
                .setHeader(QUERY_STRING, simple("${exchangeProperty." + TIAMAT_EXPORT_TASKS + ".currentTask.queryString}"))
                .to("direct:tiamatExport")
                .end()
                .routeId("tiamat-publish-export-start-new");


        from("direct:pollForTiamatExportStatus")
                .to("direct:tiamatPollJobStatus")

                .choice()
                .when(simple("${exchangeProperty." + GEOCODER_RESCHEDULE_TASK + "}"))
                .choice()
                .when(simple("${header." + LOOP_COUNTER + "} <= " + maxRetries))
                .setHeader(ScheduledMessage.AMQ_SCHEDULED_DELAY, constant(retryDelay))
                // Remove or ActiveMQ will think message is overdue and resend immediately
                .removeHeader("scheduledJobId")
                .otherwise()
                .log(LoggingLevel.WARN, getClass().getName(), "${exchangeProperty." + TIAMAT_EXPORT_TASKS + ".currentTask.name} timed out. Config should probably be tweaked. Not rescheduling.")
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.TIMEOUT).build()).to("direct:updateStatus")
                .to("direct:removeCurrentTask")
                .endChoice()
                .otherwise()
                .to("direct:removeCurrentTask")
                .end()
                .routeId("tiamat-publish-export-poll-status");

        from("direct:processTiamatPublishExportResults")
                // Upload versioned file and _latest
                .setHeader(FILE_HANDLE, simple(blobStoreSubdirectoryForTiamatExport + "/${exchangeProperty." + TIAMAT_EXPORT_TASKS + ".currentTask.name}_${header." + JOB_ID + "}_${date:now:yyyyMMddHHmmss}.zip"))
                .to("direct:tiamatExportMoveFileToMardukBlobStore")
                .setHeader(FILE_HANDLE, simple(blobStoreSubdirectoryForTiamatExport + "/${exchangeProperty." + TIAMAT_EXPORT_TASKS + ".currentTask.name}_latest.zip"))
                .to("direct:tiamatExportMoveFileToMardukBlobStore")

                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.OK).build()).to("direct:updateStatus")
                .log(LoggingLevel.INFO, "Finished Tiamat publish export: ${exchangeProperty." + TIAMAT_EXPORT_TASKS + ".currentTask.name}")
                .routeId("tiamat-publish-export-results");

        from("direct:removeCurrentTask")
                .removeHeader(LOOP_COUNTER)
                .process(e -> e.getProperty(TIAMAT_EXPORT_TASKS, TiamatExportTasks.class).popNextTask())
                .routeId("tiamat-publish-exports-clean-current-task");
    }


}

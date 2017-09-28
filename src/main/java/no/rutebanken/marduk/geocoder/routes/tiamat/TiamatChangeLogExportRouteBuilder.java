package no.rutebanken.marduk.geocoder.routes.tiamat;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.geocoder.routes.tiamat.model.TiamatExportTask;
import no.rutebanken.marduk.geocoder.routes.tiamat.model.TiamatExportTasks;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static no.rutebanken.marduk.Constants.*;

/**
 * Route for triggering synchronous exports of changed stop places in an interval from Tiamat.
 */
@Component
public class TiamatChangeLogExportRouteBuilder extends BaseRouteBuilder {


    @Value("${tiamat.change.log.download.directory:files/tiamat/changelog}")
    private String localWorkingDirectory;

    @Value("${tiamat.url}")
    private String tiamatUrl;

    @Value("${tiamat.change.log.path:/services/stop_places/netex/changed_in_period}")
    private String changeLogPath;

    @Value("${tiamat.publish.export.blobstore.subdirectory:tiamat}")
    private String blobStoreSubdirectoryForTiamatExport;

    @Value("${tiamat.change.log.key.prefix:/v2/keys/dynamic/marduk/tiamat/change_log}")
    private String etcdKeyPrefix;

    @Value("${tiamat.change.log.per.page:200000}")
    private int maxStopsPerPage;

    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSXXXX";

    private static DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

    private static final String CNT = "RutebankenTiamatChangeLogExportCounter";

    private static final String FROM = "RutebankenTiamatChangeLogExportFrom";

    private static final String TO = "RutebankenTiamatChangeLogExportTo";


    @Override
    public void configure() throws Exception {
        super.configure();


        from("direct:processTiamatChangeLogExportTask")

                // Get state for export from etcd
                .to("direct:fetchEtcdStateForExport")

                .process(e -> e.getIn().setHeader(QUERY_STRING, createQueryUrl(e)))
                .to("direct:exportChangedStopPlaces")

                .choice().when(body().isNull())
                .log(LoggingLevel.INFO, "Finished Tiamat publish export: ${exchangeProperty." + TIAMAT_EXPORT_TASKS + ".currentTask.name} with no new data")
                .otherwise()

                .to("direct:uploadChangeLogAsZipBlob")

                // Update state in etcd
                .to("direct:updateEtcdStateForExport")
                .log(LoggingLevel.INFO, "Finished Tiamat publish export: ${exchangeProperty." + TIAMAT_EXPORT_TASKS + ".currentTask.name}. Uploaded new file ${header." + FILE_HANDLE + "}")

                .end()
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.OK).build()).to("direct:updateStatus")
                .routeId("tiamat-publish-export-process-changelog");


        from("direct:exportChangedStopPlaces")
                .log(LoggingLevel.DEBUG, "Start Tiamat export for change log")
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                .setBody(constant(null))
                .toD(tiamatUrl + changeLogPath + "/${header." + QUERY_STRING + "}")
                .log(LoggingLevel.INFO, "Completed Tiamat export for change log")
                .routeId("tiamat-export-changelog-route");


        from("direct:uploadChangeLogAsZipBlob")
                .toD("file:" + localWorkingDirectory + "?fileName=content/_stops.xml")
                .process(e -> e.getIn().setBody(ZipFileUtils.zipFilesInFolder(localWorkingDirectory + "/content", localWorkingDirectory + "/result.zip")))
                .setHeader(FILE_HANDLE, simple(blobStoreSubdirectoryForTiamatExport + "/${exchangeProperty." + TIAMAT_EXPORT_TASKS + ".currentTask.name}" +
                                                       "_${header." + CNT + "}_${header." + FROM + "}-${header." + TO + "}.zip"))
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(true))
                .to("direct:uploadBlob")

                .routeId("tiamat-export-changelog-upload-zip-blob");

        from("direct:fetchEtcdStateForExport")
                .setHeader(Constants.ETCD_KEY, simple(etcdKeyPrefix + "/${exchangeProperty." + TIAMAT_EXPORT_TASKS + ".currentTask.name}_cnt"))
                .to("direct:getEtcdValue")
                .process(e -> e.getIn().setHeader(CNT, e.getIn().getBody() == null ? 0 : e.getIn().getBody(Integer.class)))

                .setHeader(Constants.ETCD_KEY, simple(etcdKeyPrefix + "/${exchangeProperty." + TIAMAT_EXPORT_TASKS + ".currentTask.name}_to"))
                .to("direct:getEtcdValue")
                .choice().when(body().isNull())
                // Set base line at last midnight. Update to etcd to fixate it in case there are no changes
                .setBody(constant(toString(LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC))))
                .to("direct:setEtcdValue")
                .end()

                .setHeader(FROM, simple("${body}"))
                .process(e -> e.getIn().setHeader(TO, toString(Instant.now())))
                .routeId("tiamat-publish-export-changelog-fetch-state");

        from("direct:updateEtcdStateForExport")
                .process(e -> e.getIn().setBody(e.getIn().getHeader(CNT, Integer.class) + 1))
                .setHeader(Constants.ETCD_KEY, simple(etcdKeyPrefix + "/${exchangeProperty." + TIAMAT_EXPORT_TASKS + ".currentTask.name}_cnt"))
                .to("direct:setEtcdValue")
                .setBody(simple("${header." + TO + "}"))
                .setHeader(Constants.ETCD_KEY, simple(etcdKeyPrefix + "/${exchangeProperty." + TIAMAT_EXPORT_TASKS + ".currentTask.name}_to"))
                .to("direct:setEtcdValue")
                .routeId("tiamat-publish-export-changelog-update-state");
    }

    private String toString(Instant instant) {
        return instant.atZone(ZoneId.of("UTC")).format(FORMATTER);
    }

    private String createQueryUrl(Exchange e) {
        TiamatExportTask task = e.getProperty(TIAMAT_EXPORT_TASKS, TiamatExportTasks.class).getCurrentTask();
        StringBuilder query = new StringBuilder(task.getQueryString());
        if (query.length() > 0) {
            query.append("&");
        } else {
            query.append("?");
        }
        query.append("from=").append(e.getIn().getHeader(FROM));
        query.append("&to=").append(e.getIn().getHeader(TO));
        query.append("&=per_page").append(maxStopsPerPage);
        return query.toString();
    }
}

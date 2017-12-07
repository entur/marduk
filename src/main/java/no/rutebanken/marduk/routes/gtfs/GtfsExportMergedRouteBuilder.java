package no.rutebanken.marduk.routes.gtfs;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.GtfsFileUtils;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

import static no.rutebanken.marduk.Constants.*;

/**
 * Route combining gtfs exports from all providers.
 */
@Component
public class GtfsExportMergedRouteBuilder  extends BaseRouteBuilder {


    @Value("${gtfs.export.download.directory:files/gtfs/merged}")
    private String localWorkingDirectory;

    @Value("${gtfs.norway.merged.file.name:rb_norway-aggregated-gtfs.zip}")
    private String gtfsNorwayMergedFileName;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("activemq:queue:GtfsExportMergedQueue?transacted=true&maxConcurrentConsumers=1&messageListenerContainerFactoryRef=batchListenerContainerFactory").autoStartup("{{gtfs.export.autoStartup:true}}")
                .transacted()
                .to("direct:exportMergedGtfs")
                .setBody(constant(null))
                .inOnly("activemq:queue:GoogleExportQueue")
                .routeId("gtfs-export-merged-jms-route");

        from("direct:exportMergedGtfs")
                .log(LoggingLevel.INFO, getClass().getName(), "Start export of merged GTFS file for Norway")

                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.TIMETABLE_PUBLISH).action("EXPORT_GTFS_MERGED").fileName(gtfsNorwayMergedFileName).state(JobEvent.State.STARTED).newCorrelationId().build())
                .to("direct:updateStatus")

                .setHeader(Exchange.FILE_PARENT, constant(localWorkingDirectory))
                .to("direct:cleanUpLocalDirectory")

                .to("direct:fetchLatestGtfs")
                .to("direct:mergeGtfs")
                .to("direct:uploadMergedGtfs")

                .to("direct:cleanUpLocalDirectory")
                // Use wire tap to avoid replacing body
                .wireTap("direct:reportExportMergedGtfsOK")

                .log(LoggingLevel.INFO, getClass().getName(), "Completed export of merged GTFS file for Norway")
                .routeId("gtfs-export-merged-route");


        from("direct:reportExportMergedGtfsOK")
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.OK).build())
                .to("direct:updateStatus")
                .routeId("gtfs-export-merged-report-ok");

        from("direct:fetchLatestGtfs")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching gtfs files for all providers.")
                .setBody(simple(getAggregatedGtfsFiles()))
                .split(body())
                .to("direct:getGtfsFiles")
                .routeId("gtfs-export-fetch-latest");

        from("direct:getGtfsFiles")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Fetching " + BLOBSTORE_PATH_OUTBOUND + "gtfs/${body}")
                .setProperty("fileName", body())
                .setHeader(FILE_HANDLE, simple(BLOBSTORE_PATH_OUTBOUND + "gtfs/${property.fileName}"))
                .to("direct:getBlob")
                .choice()
                .when(body().isNotEqualTo(null))
                .toD("file:" + localWorkingDirectory + "?fileName=${property." + TIMESTAMP + "}/org/${property.fileName}")
                .otherwise()
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "${property.fileName} was empty when trying to fetch it from blobstore.")
                .routeId("gtfs-export-get-latest-for-provider");

        from("direct:mergeGtfs")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Merging gtfs files for all providers.")
                .setBody(simple(localWorkingDirectory + "/${property." + TIMESTAMP + "}/org"))
                .bean(method(GtfsFileUtils.class, "mergeGtfsFilesInDirectory"))
                .toD("file:" + localWorkingDirectory + "?fileName=${property." + TIMESTAMP + "}/org/merged.zip")

                .routeId("gtfs-export-merge");

        from("direct:uploadMergedGtfs")
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(true))
                .setHeader(FILE_HANDLE, simple(BLOBSTORE_PATH_OUTBOUND + "gtfs/" + gtfsNorwayMergedFileName))
                .to("direct:uploadBlob")
                .log(LoggingLevel.INFO, getClass().getName(), "Uploaded new combined Gtfs for Norway, triggering async export to Google")
                .routeId("gtfs-export-upload-merged");


    }

    String getAggregatedGtfsFiles() {
        return getProviderRepository().getProviders().stream()
                       .filter(p -> p.chouetteInfo.migrateDataToProvider == null)
                       .map(p -> p.chouetteInfo.referential + "-" + CURRENT_AGGREGATED_GTFS_FILENAME)
                       .collect(Collectors.joining(","));
    }



}


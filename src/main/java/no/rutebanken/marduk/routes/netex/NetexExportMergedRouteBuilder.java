package no.rutebanken.marduk.routes.netex;

import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Collectors;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;

/**
 * Route combining netex exports per provider with stop place export for a common netex export for Norway.
 */
@Component
public class NetexExportMergedRouteBuilder extends BaseRouteBuilder {

    @Value("${netex.export.download.directory:files/netex/merged}")
    private String localWorkingDirectory;

    @Value("${netex.export.stop.place.blob.path:tiamat/CurrentAndFuture_latest.zip}")
    private String stopPlaceExportBlobPath;

    @Value("${netex.export.file.path:netex/rb_norway-aggregated-netex.zip}")
    private String netexExportMergedFilePath;

    @Value("${netex.export.stops.file.prefix:_stops}")
    private String netexExportStopsFilePrefix;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("activemq:queue:NetexExportMergedQueue?transacted=true&maxConcurrentConsumers=1&messageListenerContainerFactoryRef=batchListenerContainerFactory").autoStartup("{{otp.graph.build.autoStartup:true}}")
                .transacted()
                .log(LoggingLevel.INFO, getClass().getName(), "Start export of merged Netex file for Norway")

                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.TIMETABLE_PUBLISH).action("EXPORT_NETEX_MERGED").fileName(netexExportStopsFilePrefix).state(JobEvent.State.STARTED).newCorrelationId().build())
                .to("direct:updateStatus")

                .setHeader(Exchange.FILE_PARENT, constant(localWorkingDirectory))
                .to("direct:cleanUpLocalDirectory")

                .to("direct:fetchLatestProviderNetexExports")
                .to("direct:fetchStopsNetexExport")

                .to("direct:mergeNetex")

                .to("direct:cleanUpLocalDirectory")

                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.OK).build()).to("direct:updateStatus")
                .log(LoggingLevel.INFO, getClass().getName(), "Completed export of merged Netex file for Norway")
                .routeId("netex-export-merged-route");


        from("direct:fetchLatestProviderNetexExports")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching netex files for all providers.")
                .process(e -> e.getIn().setBody(getAggregatedNetexFiles()))
                .split(body())
                .to("direct:fetchProviderNetexExport")
                .routeId("netex-export-fetch-latest-per-provider");


        from("direct:fetchProviderNetexExport")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching " + BLOBSTORE_PATH_OUTBOUND + "netex/${body}")
                .setProperty("fileName", body())
                .setHeader(FILE_HANDLE, simple(BLOBSTORE_PATH_OUTBOUND + "netex/${property.fileName}"))
                .to("direct:getBlob")
                .choice()
                .when(body().isNotEqualTo(null))
                .process(e -> ZipFileUtils.unzipFile(e.getIn().getBody(InputStream.class), localWorkingDirectory))
                .otherwise()
                .log(LoggingLevel.INFO, getClass().getName(), "${property.fileName} was empty when trying to fetch it from blobstore.")
                .routeId("netex-export-fetch-latest-for-provider");


        from("direct:fetchStopsNetexExport")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching " + stopPlaceExportBlobPath)
                .setProperty("fileName", body())
                .setHeader(FILE_HANDLE, simple(stopPlaceExportBlobPath))
                .to("direct:getBlob")
                .choice()
                .when(body().isNotEqualTo(null))
                .process(e -> ZipFileUtils.unzipFile(e.getIn().getBody(InputStream.class), localWorkingDirectory + "/stops"))
                .process(e -> copyStopFiles(localWorkingDirectory + "/stops"))

                .otherwise()
                .log(LoggingLevel.WARN, getClass().getName(), "No stop place export found, unable to create merged Netex for Norway")
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.FAILED).build()).to("direct:updateStatus")
                .stop()
                .routeId("netex-export-fetch-latest-for-stops");

        from("direct:mergeNetex")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Merging Netex files for all providers and stop place registry.")
                .process(e -> new File(localWorkingDirectory + "/result").mkdir())
                .process(e -> e.getIn().setBody(ZipFileUtils.zipFilesInFolder(localWorkingDirectory, localWorkingDirectory + "/result/merged.zip")))
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(true))
                .setHeader(FILE_HANDLE, simple(BLOBSTORE_PATH_OUTBOUND + netexExportMergedFilePath))
                .to("direct:uploadBlob")
                .log(LoggingLevel.INFO, getClass().getName(), "Uploaded new combined Netex for Norway")
                .routeId("netex-export-merge-file");

    }

    String getAggregatedNetexFiles() {
        return getProviderRepository().getProviders().stream()
                       .filter(p -> p.chouetteInfo.migrateDataToProvider == null)
                       .map(p -> p.chouetteInfo.referential + "-" + CURRENT_AGGREGATED_NETEX_FILENAME)
                       .collect(Collectors.joining(","));
    }


    /**
     * Copy stop files from stop place registry to ensure they are given a name in compliance with profile.
     */
    private void copyStopFiles(String sourceDir) {
        try {
            int i = 0;
            for (File stopFile : FileUtils.listFiles(new File(sourceDir), null, false)) {
                String targetFileName = netexExportStopsFilePrefix + (i > 0 ? i : "") + ".xml";
                FileUtils.copyFile(stopFile, new File(localWorkingDirectory + "/" + targetFileName));
            }
        } catch (IOException ioe) {
            throw new MardukException("Failed to copy/rename stop files from NSR: " + ioe.getMessage(), ioe);
        }
    }

}

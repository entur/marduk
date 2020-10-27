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

package no.rutebanken.marduk.routes.otp.otp1;

import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

import static no.rutebanken.marduk.Constants.BLOBSTORE_MAKE_BLOB_PUBLIC;
import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.CURRENT_AGGREGATED_NETEX_FILENAME;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FOLDER_NAME;

/**
 * Route combining netex exports per provider with stop place export for a common netex export for Norway.
 */
@Component
public class NetexExportMergedRouteBuilder extends BaseRouteBuilder {

    private static final String UNPACKED_NETEX_SUBFOLDER = "/unpacked-netex";
    private static final String STOPS_FILES_SUBFOLDER = "/stops";
    private static final String MERGED_NETEX_SUBFOLDER = "/result";

    @Value("${netex.export.download.directory:files/netex/merged}")
    private String localWorkingDirectory;

    @Value("${netex.export.stop.place.blob.path:tiamat/Full_latest.zip}")
    private String stopPlaceExportBlobPath;

    @Value("${netex.export.file.path:netex/rb_norway-aggregated-netex.zip}")
    private String netexExportMergedFilePath;

    @Value("${netex.export.stops.file.prefix:_stops}")
    private String netexExportStopsFilePrefix;



    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:exportMergedNetex")
                .log(LoggingLevel.INFO, getClass().getName(), "Start export of merged Netex file for Norway")

                .setProperty(FOLDER_NAME, simple(localWorkingDirectory + "/${header." + CORRELATION_ID + "}_${date:now:yyyyMMddHHmmssSSS}"))

                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.TIMETABLE_PUBLISH).action("EXPORT_NETEX_MERGED").fileName(netexExportStopsFilePrefix).state(JobEvent.State.STARTED).newCorrelationId().build())
                .to(ExchangePattern.InOnly, "direct:updateStatus")

                .setHeader(Exchange.FILE_PARENT, simple("${exchangeProperty."+FOLDER_NAME+"}"))
                .to("direct:cleanUpLocalDirectory")

                .doTry()
                .to("direct:fetchLatestProviderNetexExports")
                .to("direct:fetchStopsNetexExport")
                .to("direct:mergeNetex")
                // Use wire tap to avoid replacing body
                .wireTap("direct:reportExportMergedNetexOK")
                .end()
                .log(LoggingLevel.INFO, getClass().getName(), "Completed export of merged Netex file for Norway")
                .doFinally()
                .to("direct:cleanUpLocalDirectory")
                .end()
                .routeId("netex-export-merged-route");

        from("direct:reportExportMergedNetexOK")
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.OK).build())
                .to(ExchangePattern.InOnly, "direct:updateStatus")
                .routeId("netex-export-merged-report-ok");

        from("direct:fetchLatestProviderNetexExports")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching netex files for all providers.")
                .process(e -> e.getIn().setBody(getAggregatedNetexFiles()))
                .split(body())
                .to("direct:fetchProviderNetexExport")
                .routeId("netex-export-fetch-latest-per-provider");


        from("direct:fetchProviderNetexExport")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching " + BLOBSTORE_PATH_OUTBOUND + "netex/${body}")
                .setProperty("fileName", body())
                .setHeader(FILE_HANDLE, simple(BLOBSTORE_PATH_OUTBOUND + "netex/${exchangeProperty.fileName}"))
                .to("direct:getBlob")
                .choice()
                .when(body().isNotEqualTo(null))
                .process(e -> ZipFileUtils.unzipFile(e.getIn().getBody(InputStream.class), e.getProperty(FOLDER_NAME, String.class)  + UNPACKED_NETEX_SUBFOLDER))
                .otherwise()
                .log(LoggingLevel.INFO, getClass().getName(), "${exchangeProperty.fileName} was empty when trying to fetch it from blobstore.")
                .routeId("netex-export-fetch-latest-for-provider");


        from("direct:fetchStopsNetexExport")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching " + stopPlaceExportBlobPath)
                .setProperty("fileName", body())
                .setHeader(FILE_HANDLE, simple(stopPlaceExportBlobPath))
                .to("direct:getBlob")
                .choice()
                .when(body().isNotEqualTo(null))
                .process(e -> ZipFileUtils.unzipFile(e.getIn().getBody(InputStream.class),  e.getProperty(FOLDER_NAME, String.class) + STOPS_FILES_SUBFOLDER))
                .process(e -> copyAndRenameStopFiles( e.getProperty(FOLDER_NAME, String.class) + STOPS_FILES_SUBFOLDER, e.getProperty(FOLDER_NAME, String.class) + UNPACKED_NETEX_SUBFOLDER))

                .otherwise()
                .log(LoggingLevel.WARN, getClass().getName(), "No stop place export found, unable to create merged Netex for Norway")
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.FAILED).build()).to("direct:updateStatus")
                .stop()
                .routeId("netex-export-fetch-latest-for-stops");

        from("direct:mergeNetex").streamCaching()
                .log(LoggingLevel.DEBUG, getClass().getName(), "Merging Netex files for all providers and stop place registry.")
                .process(e -> new File( e.getProperty(FOLDER_NAME, String.class) + MERGED_NETEX_SUBFOLDER).mkdir())
                .process(e -> e.getIn().setBody(ZipFileUtils.zipFilesInFolder( e.getProperty(FOLDER_NAME, String.class) + UNPACKED_NETEX_SUBFOLDER,  e.getProperty(FOLDER_NAME, String.class) + MERGED_NETEX_SUBFOLDER + "/merged.zip")))
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, simple("true", Boolean.class))
                .setHeader(FILE_HANDLE, simple(BLOBSTORE_PATH_OUTBOUND + netexExportMergedFilePath))
                .to("direct:uploadBlob")
                .log(LoggingLevel.INFO, getClass().getName(), "Uploaded new combined Netex for Norway")
                .routeId("netex-export-merge-file");

    }

    List<String> getAggregatedNetexFiles() {
        return getProviderRepository().getProviders().stream()
                       .filter(p -> p.chouetteInfo.migrateDataToProvider == null)
                       .map(p -> p.chouetteInfo.referential + "-" + CURRENT_AGGREGATED_NETEX_FILENAME)
                       .collect(Collectors.toList());
    }


    /**
     * Copy stop files from stop place registry to ensure they are given a name in compliance with profile.
     */
    private void copyAndRenameStopFiles(String sourceDir, String targetDir) {
        try {
            int i = 0;
            for (File stopFile : FileUtils.listFiles(new File(sourceDir), null, false)) {
                String targetFileName = netexExportStopsFilePrefix + (i > 0 ? i : "") + ".xml";
                FileUtils.copyFile(stopFile, new File(targetDir, targetFileName));
                i++;
            }
        } catch (IOException ioe) {
            throw new MardukException("Failed to copy/rename stop files from NSR: " + ioe.getMessage(), ioe);
        }
    }

}

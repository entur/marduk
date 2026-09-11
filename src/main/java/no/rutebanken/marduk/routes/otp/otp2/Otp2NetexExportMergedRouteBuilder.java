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

package no.rutebanken.marduk.routes.otp.otp2;

import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import no.rutebanken.marduk.routes.netex.NetexDsjExportConfig;
import no.rutebanken.marduk.routes.netex.NetexDsjExportConfig.Variant;
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
import java.nio.file.Paths;
import java.util.List;

import static no.rutebanken.marduk.Constants.*;

/**
 * Route combining netex exports per provider with stop place export for a common netex export for Norway.
 * <p>
 * When the dual DatedServiceJourney export is enabled (see {@link NetexDsjExportConfig}), the aggregated export is
 * built twice, from the legacy (NeTEx 1.15) and from the new (NeTEx 1.16) per-provider exports, and stored in the
 * corresponding folders. The variant configured as default is then copied into the default folder.
 */
@Component
public class Otp2NetexExportMergedRouteBuilder extends BaseRouteBuilder {

    private static final String UNPACKED_NETEX_SUBFOLDER = "/unpacked-netex";
    private static final String STOPS_FILES_SUBFOLDER = "/stops";
    private static final String MERGED_NETEX_SUBFOLDER = "/result";

    /**
     * Folder of the public bucket from which the per-provider exports are fetched, for instance {@code outbound/netex/}.
     */
    private static final String PROP_SOURCE_FOLDER = "RutebankenNetexExportSourceFolder";
    /**
     * Path of the merged export in the public bucket.
     */
    private static final String PROP_TARGET_FILE_HANDLE = "RutebankenNetexExportTargetFileHandle";
    @Value("${otp2.netex.export.download.directory:files/netex/merged-otp2}")
    private String localWorkingDirectory;

    @Value("${netex.export.stop.place.blob.path:tiamat/Full_latest.zip}")
    private String stopPlaceExportBlobPath;

    @Value("${netex.export.file.path:netex/rb_norway-aggregated-netex.zip}")
    private String netexExportMergedFilePath;

    @Value("${netex.export.stops.file.prefix:_stops}")
    private String netexExportStopsFilePrefix;

    private final NetexDsjExportConfig netexDsjExportConfig;

    public Otp2NetexExportMergedRouteBuilder(NetexDsjExportConfig netexDsjExportConfig) {
        this.netexDsjExportConfig = netexDsjExportConfig;
    }

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:otp2ExportMergedNetex")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Start export of merged Netex file for Norway for OTP2")

                .setProperty(FOLDER_NAME, simple(localWorkingDirectory + "/${header." + CORRELATION_ID + "}_${date:now:yyyyMMddHHmmssSSS}"))

                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.TIMETABLE_PUBLISH).action(JobEvent.TimetableAction.EXPORT_NETEX_MERGED).fileName(netexExportStopsFilePrefix).state(JobEvent.State.STARTED).newCorrelationId().build())
                .to(ExchangePattern.InOnly, "direct:updateStatus")

                .setHeader(Exchange.FILE_PARENT, simple("${exchangeProperty."+FOLDER_NAME+"}"))
                .to("direct:cleanUpLocalDirectory")

                .doTry()
                .to("direct:otp2FetchStopsNetexExport")
                .to("direct:otp2BuildMergedNetexExports")
                // Use wire tap to avoid replacing body
                .wireTap("direct:otp2ReportExportMergedNetexOK")
                .end()
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Completed export of merged Netex file for Norway for OTP2")
                .doFinally()
                .to("direct:cleanUpLocalDirectory")
                .end()
                .routeId("otp2-netex-export-merged-route");

        from("direct:otp2ReportExportMergedNetexOK")
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.OK).build())
                .to(ExchangePattern.InOnly, "direct:updateStatus")
                .routeId("otp2-netex-export-merged-report-ok");

        // Sub-route to avoid a choice() nested in the doTry() of the main route.
        from("direct:otp2BuildMergedNetexExports")
                .choice()
                .when(constant(netexDsjExportConfig.isEnabled()))
                    .to("direct:otp2ExportMergedNetexDsjVariants")
                .otherwise()
                    .setProperty(PROP_SOURCE_FOLDER, constant(NetexDsjExportConfig.DEFAULT_EXPORT_PATH))
                    .setProperty(PROP_TARGET_FILE_HANDLE, constant(BLOBSTORE_PATH_OUTBOUND + netexExportMergedFilePath))
                    .to("direct:otp2ExportMergedNetexVariant")
                .end()
                .routeId("otp2-netex-export-build-merged-exports");

        // Build the aggregated export for the new (NeTEx 1.16) and legacy (NeTEx 1.15) variants of the per-provider
        // exports, then copy the default variant into the default folder.
        from("direct:otp2ExportMergedNetexDsjVariants")
                // providers that have not published since the dual export was enabled exist only in the default folder
                .to("direct:distributeMissingDsjNetexExports")

                .setProperty(PROP_SOURCE_FOLDER, constant(netexDsjExportConfig.variantFolder(Variant.NEW)))
                .setProperty(PROP_TARGET_FILE_HANDLE, constant(netexDsjExportConfig.variantPath(Variant.NEW, mergedNetexFileName())))
                .to("direct:otp2ExportMergedNetexVariant")

                .setProperty(PROP_SOURCE_FOLDER, constant(netexDsjExportConfig.variantFolder(Variant.LEGACY)))
                .setProperty(PROP_TARGET_FILE_HANDLE, constant(netexDsjExportConfig.variantPath(Variant.LEGACY, mergedNetexFileName())))
                .to("direct:otp2ExportMergedNetexVariant")

                .setHeader(FILE_HANDLE, constant(netexDsjExportConfig.variantPath(netexDsjExportConfig.getDefaultVariant(), mergedNetexFileName())))
                .setHeader(TARGET_FILE_HANDLE, constant(BLOBSTORE_PATH_OUTBOUND + netexExportMergedFilePath))
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Copying the " + netexDsjExportConfig.getDefaultVariant() + " variant of the combined Netex for Norway to the default folder")
                .to("direct:copyBlobInBucket")
                .routeId("otp2-netex-export-merged-dsj-variants");

        // Build one aggregated export from the per-provider exports found in the source folder and the stop place
        // export, and upload it to the target file handle. The stop place export contains no DatedServiceJourney and
        // is used as is in every variant.
        from("direct:otp2ExportMergedNetexVariant")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Building combined Netex for Norway from ${exchangeProperty." + PROP_SOURCE_FOLDER + "}")
                .to("direct:otp2FetchLatestProviderNetexExports")
                .process(e -> copyAndRenameStopFiles(e.getProperty(FOLDER_NAME, String.class) + STOPS_FILES_SUBFOLDER, e.getProperty(FOLDER_NAME, String.class) + UNPACKED_NETEX_SUBFOLDER))
                .to("direct:otp2MergeNetex")
                // free the disk space before building the next variant
                .process(e -> FileUtils.deleteDirectory(new File(e.getProperty(FOLDER_NAME, String.class) + UNPACKED_NETEX_SUBFOLDER)))
                .process(e -> FileUtils.deleteDirectory(new File(e.getProperty(FOLDER_NAME, String.class) + MERGED_NETEX_SUBFOLDER)))
                .routeId("otp2-netex-export-merged-variant");

        from("direct:otp2FetchLatestProviderNetexExports")
                .log(LoggingLevel.DEBUG, getClass().getName(), correlation() + "Fetching netex files for all providers.")
                .process(e -> e.getIn().setBody(getAggregatedNetexFiles()))
                .split(body())
                .to("direct:otp2FetchProviderNetexExport")
                .routeId("otp2-netex-export-fetch-latest-per-provider");


        from("direct:otp2FetchProviderNetexExport")
                .log(LoggingLevel.DEBUG, getClass().getName(), correlation() + "Fetching ${exchangeProperty." + PROP_SOURCE_FOLDER + "}${body}")
                .setProperty("fileName", body())
                .setHeader(FILE_HANDLE, simple("${exchangeProperty." + PROP_SOURCE_FOLDER + "}${exchangeProperty.fileName}"))
                .to("direct:getBlob")
                .choice()
                .when(body().isNotEqualTo(null))
                .process(e -> ZipFileUtils.unzipFile(e.getIn().getBody(InputStream.class), e.getProperty(FOLDER_NAME, String.class)  + UNPACKED_NETEX_SUBFOLDER))
                .otherwise()
                .log(LoggingLevel.WARN, getClass().getName(), correlation() + "${header." + FILE_HANDLE + "} was empty when trying to fetch it from blobstore.")
                .routeId("otp2-netex-export-fetch-latest-for-provider");


        from("direct:otp2FetchStopsNetexExport")
                .log(LoggingLevel.DEBUG, getClass().getName(), correlation() + "Fetching " + stopPlaceExportBlobPath)
                .setProperty("fileName", body())
                .setHeader(FILE_HANDLE, simple(stopPlaceExportBlobPath))
                .to("direct:getBlob")
                .choice()
                .when(body().isNotEqualTo(null))
                .process(e -> ZipFileUtils.unzipFile(e.getIn().getBody(InputStream.class),  e.getProperty(FOLDER_NAME, String.class) + STOPS_FILES_SUBFOLDER))
                .otherwise()
                .log(LoggingLevel.WARN, getClass().getName(), correlation() + "No stop place export found, unable to create merged Netex for Norway")
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.FAILED).build()).to("direct:updateStatus")
                .stop()
                .routeId("otp2-netex-export-fetch-latest-for-stops");

        from("direct:otp2MergeNetex").streamCaching()
                .log(LoggingLevel.DEBUG, getClass().getName(), correlation() + "Merging Netex files for all providers and stop place registry.")
                .process(e -> new File( e.getProperty(FOLDER_NAME, String.class) + MERGED_NETEX_SUBFOLDER).mkdir())
                .process(e -> e.getIn().setBody(ZipFileUtils.zipFilesInFolder( e.getProperty(FOLDER_NAME, String.class) + UNPACKED_NETEX_SUBFOLDER,  e.getProperty(FOLDER_NAME, String.class) + MERGED_NETEX_SUBFOLDER + "/merged.zip")))
                .setHeader(FILE_HANDLE, simple("${exchangeProperty." + PROP_TARGET_FILE_HANDLE + "}"))
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Uploading new combined Netex for Norway for OTP to ${header." + FILE_HANDLE + "}")
                .to("direct:uploadBlob")
                .routeId("otp2-netex-export-merge-file");

    }

    List<String> getAggregatedNetexFiles() {
        return getProviderRepository().getProviders().stream()
                       .filter(p -> p.getChouetteInfo().getMigrateDataToProvider() == null)
                       .map(p -> p.getChouetteInfo().getReferential() + "-" + CURRENT_AGGREGATED_NETEX_FILENAME)
                       .toList();
    }

    /**
     * Name of the merged export file, without its folder (for instance {@code rb_norway-aggregated-netex.zip}).
     */
    private String mergedNetexFileName() {
        return Paths.get(netexExportMergedFilePath).getFileName().toString();
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

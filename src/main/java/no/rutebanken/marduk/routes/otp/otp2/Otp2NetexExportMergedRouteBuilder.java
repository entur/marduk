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

import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.RawZipMerger;
import no.rutebanken.marduk.routes.netex.NetexDsjExportConfig;
import no.rutebanken.marduk.routes.netex.NetexDsjExportConfig.Variant;
import no.rutebanken.marduk.routes.netex.NetexDsjExportRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static no.rutebanken.marduk.Constants.*;

/**
 * Route combining netex exports per provider with stop place export for a common netex export for Norway.
 * <p>
 * When the dual DatedServiceJourney export is enabled (see {@link NetexDsjExportConfig}), the aggregated export is
 * built twice, from the legacy (NeTEx 1.15 structure) and from the new (NeTEx 1.16 structure) per-provider exports, and stored in the
 * corresponding folders. The variant configured as default is then copied into the default folder.
 */
@Component
public class Otp2NetexExportMergedRouteBuilder extends BaseRouteBuilder {

    private static final String MERGED_NETEX_SUBFOLDER = "/result";

    /**
     * Folder of the public bucket from which the per-provider exports are fetched, for instance {@code outbound/netex/}.
     */
    private static final String PROP_SOURCE_FOLDER = "RutebankenNetexExportSourceFolder";
    /**
     * Path of the merged export in the public bucket.
     */
    private static final String PROP_TARGET_FILE_HANDLE = "RutebankenNetexExportTargetFileHandle";
    /**
     * Per-provider exports that were not found in the folder of the variant being built.
     */
    private static final String PROP_MISSING_PROVIDER_EXPORTS = "RutebankenNetexExportMissingProviderExports";
    /**
     * The same, for the first variant built, against which the following variants are checked.
     */
    private static final String PROP_REFERENCE_MISSING_PROVIDER_EXPORTS = "RutebankenNetexExportReferenceMissingProviderExports";
    /**
     * The per-provider exports downloaded for the variant being built, by file name.
     */
    private static final String PROP_PROVIDER_ARCHIVES = "RutebankenNetexExportProviderArchives";
    /**
     * The stop place export, downloaded once and used as is in every variant.
     */
    private static final String PROP_STOPS_ARCHIVE = "RutebankenNetexExportStopsArchive";
    /**
     * The per-provider export file names to aggregate, derived once for the whole export from the snapshotted
     * published providers.
     */
    private static final String PROP_PROVIDER_EXPORT_FILES = "RutebankenNetexExportProviderExportFiles";
    @Value("${otp2.netex.export.download.directory:files/netex/merged-otp2}")
    private String localWorkingDirectory;

    @Value("${netex.export.stop.place.blob.path:tiamat/Full_latest.zip}")
    private String stopPlaceExportBlobPath;

    @Value("${netex.export.file.path:netex/rb_norway-aggregated-netex.zip}")
    private String netexExportMergedFilePath;

    @Value("${netex.export.stops.file.prefix:_stops}")
    private String netexExportStopsFilePrefix;

    /**
     * Fail the aggregated export when two archives contribute an entry under the same name, instead of keeping the
     * last one as the previous implementation silently did.
     */
    @Value("${netex.export.merged.fail.on.duplicate.entry:false}")
    private boolean failOnDuplicateEntry;

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

                // read once, so that the distribution of the missing exports and every variant work on the same
                // providers: the provider cache is refreshed every few minutes, and a provider dropping out of it
                // between two readings would silently leave its export out of an aggregate without being recorded
                // as missing
                .process(this::snapshotPublishedProviders)

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

        // Build the aggregated export for the new (NeTEx 1.16 structure) and legacy (NeTEx 1.15 structure) variants of the per-provider
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
                .process(e -> e.setProperty(PROP_MISSING_PROVIDER_EXPORTS, Collections.synchronizedSet(new LinkedHashSet<String>())))
                .process(e -> e.setProperty(PROP_PROVIDER_ARCHIVES, new ConcurrentHashMap<String, byte[]>()))
                .to("direct:otp2FetchLatestProviderNetexExports")
                .process(this::verifyVariantCoversTheSameProviders)
                .to("direct:otp2MergeNetex")
                // free the memory and the disk space before building the next variant
                .process(e -> e.removeProperty(PROP_PROVIDER_ARCHIVES))
                .process(e -> FileUtils.deleteDirectory(new File(e.getProperty(FOLDER_NAME, String.class) + MERGED_NETEX_SUBFOLDER)))
                .routeId("otp2-netex-export-merged-variant");

        from("direct:otp2FetchLatestProviderNetexExports")
                .log(LoggingLevel.DEBUG, getClass().getName(), correlation() + "Fetching netex files for all providers.")
                .process(e -> e.getIn().setBody(e.getProperty(PROP_PROVIDER_EXPORT_FILES)))
                // the downloads are network-bound and independent; the archives are merged afterwards in the order
                // of PROP_PROVIDER_EXPORT_FILES, so the aggregated export does not depend on the completion order
                .split(body()).parallelProcessing().executorService("netexAggregationExecutorService")
                .to("direct:otp2FetchProviderNetexExport")
                .routeId("otp2-netex-export-fetch-latest-per-provider");


        from("direct:otp2FetchProviderNetexExport")
                .log(LoggingLevel.DEBUG, getClass().getName(), correlation() + "Fetching ${exchangeProperty." + PROP_SOURCE_FOLDER + "}${body}")
                .setProperty("fileName", body())
                .setHeader(FILE_HANDLE, simple("${exchangeProperty." + PROP_SOURCE_FOLDER + "}${exchangeProperty.fileName}"))
                .to("direct:getBlob")
                .choice()
                .when(body().isNotEqualTo(null))
                .process(this::recordProviderArchive)
                .otherwise()
                .log(LoggingLevel.WARN, getClass().getName(), correlation() + "${header." + FILE_HANDLE + "} was empty when trying to fetch it from blobstore.")
                .process(this::recordMissingProviderExport)
                .routeId("otp2-netex-export-fetch-latest-for-provider");


        from("direct:otp2FetchStopsNetexExport")
                .log(LoggingLevel.DEBUG, getClass().getName(), correlation() + "Fetching " + stopPlaceExportBlobPath)
                .setProperty("fileName", body())
                .setHeader(FILE_HANDLE, simple(stopPlaceExportBlobPath))
                .to("direct:getBlob")
                .choice()
                .when(body().isNotEqualTo(null))
                .process(e -> e.setProperty(PROP_STOPS_ARCHIVE, e.getIn().getBody(byte[].class)))
                .otherwise()
                .log(LoggingLevel.WARN, getClass().getName(), correlation() + "No stop place export found, unable to create merged Netex for Norway")
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.FAILED).build()).to("direct:updateStatus")
                .stop()
                .routeId("otp2-netex-export-fetch-latest-for-stops");

        from("direct:otp2MergeNetex").streamCaching()
                .log(LoggingLevel.DEBUG, getClass().getName(), correlation() + "Merging Netex files for all providers and stop place registry.")
                .to("direct:otp2PackMergedNetex")
                .setHeader(FILE_HANDLE, simple("${exchangeProperty." + PROP_TARGET_FILE_HANDLE + "}"))
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Uploading new combined Netex for Norway for OTP to ${header." + FILE_HANDLE + "}")
                .to("direct:uploadBlob")
                .routeId("otp2-netex-export-merge-file");

        // Packing is timed on its own by the Micrometer route policy, separately from the upload.
        from("direct:otp2PackMergedNetex")
                .process(e -> new File(e.getProperty(FOLDER_NAME, String.class) + MERGED_NETEX_SUBFOLDER).mkdirs())
                .process(this::buildAggregatedExport)
                .routeId("otp2-netex-export-pack");

    }

    /**
     * Snapshot the published providers and the file names of their exports for the whole aggregated export (see
     * {@link NetexDsjExportRouteBuilder#PROP_PUBLISHED_PROVIDERS}).
     */
    private void snapshotPublishedProviders(Exchange e) {
        List<Provider> providers = getPublishedProviders();
        e.setProperty(NetexDsjExportRouteBuilder.PROP_PUBLISHED_PROVIDERS, providers);
        e.setProperty(PROP_PROVIDER_EXPORT_FILES, providers.stream()
                .map(p -> NetexDsjExportConfig.aggregatedNetexFileName(p.getChouetteInfo().getReferential()))
                .toList());
    }

    @SuppressWarnings("unchecked")
    private void recordMissingProviderExport(Exchange e) {
        Set<String> missing = e.getProperty(PROP_MISSING_PROVIDER_EXPORTS, Set.class);
        if (missing != null) {
            missing.add(e.getProperty("fileName", String.class));
        }
    }

    /**
     * Every variant of the aggregated export must be built from the exports of the same providers. A provider export
     * that is present in the folder of one variant but missing from the folder of another would silently disappear
     * from one of the aggregated exports, and with it from the OTP graph built from that export, so the export is
     * failed instead: the caller (a publication or a graph build) retries it.
     * <p>
     * A provider that has never published has no export in any folder and is missing from every variant, which is
     * accepted.
     */
    @SuppressWarnings("unchecked")
    private void verifyVariantCoversTheSameProviders(Exchange e) {
        Set<String> missing = e.getProperty(PROP_MISSING_PROVIDER_EXPORTS, Set.class);
        Set<String> reference = e.getProperty(PROP_REFERENCE_MISSING_PROVIDER_EXPORTS, Set.class);
        if (reference == null) {
            e.setProperty(PROP_REFERENCE_MISSING_PROVIDER_EXPORTS, missing);
        } else if (!reference.equals(missing)) {
            throw new MardukException("The NeTEx exports found in " + e.getProperty(PROP_SOURCE_FOLDER, String.class)
                    + " do not cover the same providers as the ones the previous variant of the aggregated export was built from"
                    + " (missing here: " + missing + ", missing there: " + reference
                    + "): aborting to avoid publishing an incomplete aggregated export for Norway");
        }
    }

    /**
     * Name of the merged export file, without its folder (for instance {@code rb_norway-aggregated-netex.zip}).
     */
    private String mergedNetexFileName() {
        return Paths.get(netexExportMergedFilePath).getFileName().toString();
    }

    @SuppressWarnings("unchecked")
    private void recordProviderArchive(Exchange e) {
        Map<String, byte[]> archives = e.getProperty(PROP_PROVIDER_ARCHIVES, Map.class);
        archives.put(e.getProperty("fileName", String.class), e.getIn().getBody(byte[].class));
    }

    /**
     * Build the aggregated export by copying the entries of the per-provider exports and of the stop place export
     * into a single archive, without decompressing them.
     * <p>
     * The entries are already deflated and the aggregated export does not change their content, so inflating them
     * only to deflate them again was by far the dominant cost of this route. The archives are merged in the order of
     * {@code PROP_PROVIDER_EXPORT_FILES}, and the entries of each in physical order, so the aggregated export is
     * reproducible. The stop place export comes last, because its entries used to be copied into the unpacked
     * folder after the provider exports and therefore won over an entry of the same name.
     */
    private void buildAggregatedExport(Exchange e) throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, byte[]> archives = e.getProperty(PROP_PROVIDER_ARCHIVES, Map.class);
        byte[] stops = e.getProperty(PROP_STOPS_ARCHIVE, byte[].class);
        if (stops == null) {
            throw new MardukException("No stop place export available, unable to create merged Netex for Norway");
        }
        @SuppressWarnings("unchecked")
        List<String> fileNames = e.getProperty(PROP_PROVIDER_EXPORT_FILES, List.class);
        Path target = Paths.get(e.getProperty(FOLDER_NAME, String.class) + MERGED_NETEX_SUBFOLDER + "/merged.zip");

        try (RawZipMerger.Plan plan = RawZipMerger.plan().failOnDuplicateEntry(failOnDuplicateEntry)) {
            int archiveCount = 0;
            for (String fileName : fileNames) {
                byte[] archive = archives.get(fileName);
                if (archive != null) {
                    plan.add(RawZipMerger.Source.of(fileName, archive));
                    archiveCount++;
                }
            }
            plan.addRenamed(RawZipMerger.Source.of(stopPlaceExportBlobPath, stops), netexExportStopsFilePrefix, ".xml");
            archiveCount++;

            RawZipMerger.Result result = plan.writeTo(target);
            log.info("[correlationId={}] Built the combined Netex for Norway from {} archives: {} entries, {} bytes, {} duplicate entry name(s)",
                    e.getIn().getHeader(CORRELATION_ID), archiveCount, result.entriesWritten(),
                    result.bytesWritten(), result.duplicateNames().size());
        }
        e.getIn().setBody(target.toFile());
    }

}

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

package no.rutebanken.marduk.routes.netex;

import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static no.rutebanken.marduk.Constants.*;

/**
 * Distribute a freshly published NeTEx dataset to the three export folders used during the transition to the
 * NeTEx 1.16 DatedServiceJourney structure (see {@link NetexDsjExportConfig}):
 * the dataset exported by the import pipeline is stored in the "new" folder, a downgraded NeTEx 1.15 copy is
 * stored in the "legacy" folder, and the variant configured as default is copied into the default folder.
 */
@Component
public class NetexDsjExportRouteBuilder extends BaseRouteBuilder {

    private static final String PROP_DSJ_EXPORT_FOLDER = "RutebankenDsjExportFolder";
    private static final String PROP_NEW_EXPORT_EXISTS = "RutebankenDsjNewExportExists";
    private static final String PROP_SAVED_FILE_HANDLE = "RutebankenDsjSavedFileHandle";
    private static final String PROP_SAVED_FILE_VERSION = "RutebankenDsjSavedFileVersion";
    private static final String PROP_SAVED_TARGET_FILE_HANDLE = "RutebankenDsjSavedTargetFileHandle";
    private static final String DOWNGRADED_FILE_NAME = "legacy.zip";

    private final NetexDsjExportConfig netexDsjExportConfig;

    public NetexDsjExportRouteBuilder(NetexDsjExportConfig netexDsjExportConfig) {
        this.netexDsjExportConfig = netexDsjExportConfig;
    }

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:distributeDsjNetexExport")
                .validate(header(CHOUETTE_REFERENTIAL).isNotNull())
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Distributing the NeTEx export of ${header." + CHOUETTE_REFERENTIAL + "} to the legacy and new DatedServiceJourney export folders")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT_NETEX_LEGACY).state(JobEvent.State.STARTED).build())
                .to("direct:updateStatus")
                .setProperty(PROP_DSJ_EXPORT_FOLDER, simple(netexDsjExportConfig.getDownloadDirectory() + "/${header." + CORRELATION_ID + "}_${date:now:yyyyMMddHHmmssSSS}"))
                .doTry()
                    .to("direct:seedDsjNewNetexExportIfMissing")
                    .to("direct:createLegacyDsjNetexExport")
                    .setHeader(FILE_HANDLE).method(netexDsjExportConfig, "defaultVariantSourcePath")
                    .setHeader(TARGET_FILE_HANDLE).method(netexDsjExportConfig, "defaultExportPath")
                    .to("direct:copyBlobInBucket")
                    .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT_NETEX_LEGACY).state(JobEvent.State.OK).build())
                    .to("direct:updateStatus")
                .doCatch(Exception.class)
                    .log(LoggingLevel.ERROR, getClass().getName(), correlation() + "Failed to distribute the NeTEx export of ${header." + CHOUETTE_REFERENTIAL + "}: ${exception.message}")
                    .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT_NETEX_LEGACY).state(JobEvent.State.FAILED).build())
                    .to("direct:updateStatus")
                    // the default folder must not diverge from the other folders: fail the publication so that it is retried
                    .process(this::rethrowCaughtException)
                .doFinally()
                    .process(e -> deleteDirectoryRecursively(e.getProperty(PROP_DSJ_EXPORT_FOLDER, String.class)))
                .end()
                .setBody(constant(""))
                .routeId("netex-dsj-export-distribute");

        // Legacy variant of the public export: downgraded for the codespaces whose datasets contain DatedServiceJourney
        // replacement information, a plain copy of the new variant for the others.
        from("direct:createLegacyDsjNetexExport")
                .choice()
                .when(method(netexDsjExportConfig, "hasDsjReplacements"))
                    .setHeader(FILE_HANDLE).method(netexDsjExportConfig, "newExportPath")
                    .to("direct:getBlob")
                    .process(this::downgradeExport)
                    .setHeader(FILE_HANDLE).method(netexDsjExportConfig, "legacyExportPath")
                    .to("direct:uploadBlob")
                .otherwise()
                    .log(LoggingLevel.INFO, getClass().getName(), correlation() + "The datasets of ${header." + CHOUETTE_REFERENTIAL + "} contain no DatedServiceJourney replacement information: the legacy export is a copy of the new export")
                    .setHeader(FILE_HANDLE).method(netexDsjExportConfig, "newExportPath")
                    .setHeader(TARGET_FILE_HANDLE).method(netexDsjExportConfig, "legacyExportPath")
                    .to("direct:copyBlobInBucket")
                .end()
                .routeId("netex-dsj-export-create-legacy");

        // When a provider has not published since the dual export was enabled, its dataset exists only in the
        // default folder: use it as the source (backfill).
        from("direct:seedDsjNewNetexExportIfMissing")
                .setHeader(FILE_PREFIX).method(netexDsjExportConfig, "newExportPath")
                .to("direct:findBlob")
                .choice()
                .when(body().isNull())
                    .to("direct:seedDsjNewNetexExportFromDefault")
                .end()
                .setBody(constant(""))
                .routeId("netex-dsj-export-seed-new-if-missing");

        from("direct:seedDsjNewNetexExportFromDefault")
                .setHeader(FILE_PREFIX).method(netexDsjExportConfig, "defaultExportPath")
                .to("direct:findBlob")
                .choice()
                .when(body().isNull())
                    .throwException(new MardukException("No NeTEx export found for the provider"))
                .otherwise()
                    .log(LoggingLevel.INFO, getClass().getName(), correlation() + "No NeTEx export of ${header." + CHOUETTE_REFERENTIAL + "} in the new DatedServiceJourney export folder, seeding it from the default folder")
                    .setHeader(FILE_HANDLE).method(netexDsjExportConfig, "defaultExportPath")
                    .setHeader(TARGET_FILE_HANDLE).method(netexDsjExportConfig, "newExportPath")
                    .to("direct:copyBlobInBucket")
                .end()
                .routeId("netex-dsj-export-seed-new-from-default");

        // Private export (NeTEx with blocks): store a NeTEx 1.15 copy of the blocks export next to the one produced by
        // the pipeline. Called after every publication of the blocks export; a failure is reported but does not fail
        // the publication (the timetable API falls back to the export produced by the pipeline).
        from("direct:distributeDsjNetexBlocksExport")
                .filter(constant(netexDsjExportConfig.isEnabled()))
                .to("direct:doDistributeDsjNetexBlocksExport")
                .end()
                .routeId("netex-dsj-export-distribute-blocks");

        from("direct:doDistributeDsjNetexBlocksExport")
                .validate(header(CHOUETTE_REFERENTIAL).isNotNull())
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Downgrading the NeTEx blocks export of ${header." + CHOUETTE_REFERENTIAL + "} to NeTEx 1.15")
                .setProperty(PROP_SAVED_FILE_HANDLE, header(FILE_HANDLE))
                .setProperty(PROP_DSJ_EXPORT_FOLDER, simple(netexDsjExportConfig.getDownloadDirectory() + "/${header." + CORRELATION_ID + "}_${date:now:yyyyMMddHHmmssSSS}"))
                .doTry()
                    .to("direct:createLegacyDsjNetexBlocksExport")
                .doCatch(Exception.class)
                    .log(LoggingLevel.ERROR, getClass().getName(), correlation() + "Failed to downgrade the NeTEx blocks export of ${header." + CHOUETTE_REFERENTIAL + "}: ${exception.message}")
                    .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT_NETEX_LEGACY).state(JobEvent.State.FAILED).build())
                    .to("direct:updateStatus")
                .doFinally()
                    .process(e -> deleteDirectoryRecursively(e.getProperty(PROP_DSJ_EXPORT_FOLDER, String.class)))
                    .setHeader(FILE_HANDLE, exchangeProperty(PROP_SAVED_FILE_HANDLE))
                    .setBody(constant(""))
                .end()
                .routeId("netex-dsj-export-do-distribute-blocks");

        from("direct:createLegacyDsjNetexBlocksExport")
                .choice()
                .when(method(netexDsjExportConfig, "hasDsjReplacements"))
                    .setHeader(FILE_HANDLE).method(netexDsjExportConfig, "blocksExportPath")
                    .to("direct:getInternalBlob")
                    .process(this::downgradeExport)
                    .setHeader(FILE_HANDLE).method(netexDsjExportConfig, "legacyBlocksExportPath")
                    .to("direct:uploadInternalBlob")
                .otherwise()
                    .log(LoggingLevel.INFO, getClass().getName(), correlation() + "The datasets of ${header." + CHOUETTE_REFERENTIAL + "} contain no DatedServiceJourney replacement information: the legacy blocks export is a copy of the blocks export")
                    .setHeader(FILE_HANDLE).method(netexDsjExportConfig, "blocksExportPath")
                    .setHeader(TARGET_FILE_HANDLE).method(netexDsjExportConfig, "legacyBlocksExportPath")
                    .to("direct:copyInternalBlobInBucket")
                .end()
                .routeId("netex-dsj-export-create-legacy-blocks");

        // Backfill variant: providers without a blocks export are skipped silently.
        from("direct:distributeDsjNetexBlocksExportIfPresent")
                .setHeader(FILE_PREFIX).method(netexDsjExportConfig, "blocksExportPath")
                .to("direct:findInternalBlob")
                .choice()
                .when(body().isNotNull())
                    .to("direct:distributeDsjNetexBlocksExport")
                .otherwise()
                    .log(LoggingLevel.INFO, getClass().getName(), correlation() + "No NeTEx blocks export found for ${header." + CHOUETTE_REFERENTIAL + "}")
                .end()
                .setBody(constant(""))
                .routeId("netex-dsj-export-distribute-blocks-if-present");

        // Original dataset: store the dataset uploaded by the provider in the three folders of the Nisaba bucket used
        // during the transition, the same way a published dataset is stored in three folders of the public bucket.
        // This route writes the NeTEx 1.15 copy in imported-dsj-legacy and the variant selected by
        // netex.export.dsj.default.variant in the default folder imported; the caller stores the dataset as uploaded
        // in imported-dsj-new (see NetexDsjExportConfig#originalDatasetPublicationPath).
        // Expects the headers set for the upload of the original dataset: FILE_HANDLE (original dataset in the
        // internal bucket), TARGET_FILE_HANDLE (imported/<referential>/<referential>_<timestamp>.zip) and
        // TARGET_CONTAINER (Nisaba bucket). The headers are restored afterwards.
        from("direct:distributeOriginalDatasetToNisaba")
                .filter(constant(netexDsjExportConfig.isEnabled()))
                .to("direct:doDistributeOriginalDatasetToNisaba")
                .end()
                .routeId("netex-dsj-export-distribute-original-to-nisaba");

        from("direct:doDistributeOriginalDatasetToNisaba")
                .validate(header(TARGET_FILE_HANDLE).isNotNull())
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Storing the original dataset ${header." + FILE_HANDLE + "} in the DatedServiceJourney folders of Nisaba")
                .setProperty(PROP_SAVED_FILE_HANDLE, header(FILE_HANDLE))
                .setProperty(PROP_SAVED_TARGET_FILE_HANDLE, header(TARGET_FILE_HANDLE))
                .setProperty(PROP_SAVED_FILE_VERSION, header(FILE_VERSION))
                .setProperty(PROP_DSJ_EXPORT_FOLDER, simple(netexDsjExportConfig.getDownloadDirectory() + "/${header." + CORRELATION_ID + "}_${date:now:yyyyMMddHHmmssSSS}"))
                .doTry()
                    .to("direct:createLegacyOriginalDataset")
                    .to("direct:copyDefaultVariantOriginalDatasetToNisaba")
                .doCatch(Exception.class)
                    .log(LoggingLevel.ERROR, getClass().getName(), correlation() + "Failed to store the original dataset of ${header." + CHOUETTE_REFERENTIAL + "} in the DatedServiceJourney folders of Nisaba: ${exception.message}")
                .doFinally()
                    .process(e -> deleteDirectoryRecursively(e.getProperty(PROP_DSJ_EXPORT_FOLDER, String.class)))
                    .setHeader(FILE_HANDLE, exchangeProperty(PROP_SAVED_FILE_HANDLE))
                    .setHeader(TARGET_FILE_HANDLE, exchangeProperty(PROP_SAVED_TARGET_FILE_HANDLE))
                    // direct:uploadInternalBlob overwrites FILE_VERSION with the generation of the staged copy
                    .setHeader(FILE_VERSION, exchangeProperty(PROP_SAVED_FILE_VERSION))
                    .setBody(constant(""))
                .end()
                .routeId("netex-dsj-export-do-distribute-original-to-nisaba");

        // The default folder receives the variant selected by netex.export.dsj.default.variant: the NeTEx 1.15 copy
        // staged in the internal bucket when the legacy variant is the default and the dataset was downgraded, the
        // dataset as uploaded otherwise (the two are identical for a codespace without replacement information).
        from("direct:copyDefaultVariantOriginalDatasetToNisaba")
                .process(this::setDefaultVariantOriginalDatasetHeaders)
                .to("direct:copyInternalBlobToAnotherBucket")
                .routeId("netex-dsj-export-copy-default-variant-original");

        from("direct:createLegacyOriginalDataset")
                .choice()
                .when(method(netexDsjExportConfig, "hasDsjReplacements"))
                    .to("direct:getInternalBlob")
                    .process(this::downgradeExport)
                    .process(this::setLegacyOriginalDatasetHeaders)
                    .to("direct:uploadInternalBlob")
                    .to("direct:copyInternalBlobToAnotherBucket")
                .otherwise()
                    .log(LoggingLevel.INFO, getClass().getName(), correlation() + "The datasets of ${header." + CHOUETTE_REFERENTIAL + "} contain no DatedServiceJourney replacement information: the legacy original dataset is a copy of the original dataset")
                    .process(this::setLegacyOriginalDatasetTargetHeader)
                    .to("direct:copyInternalBlobToAnotherBucket")
                .end()
                .routeId("netex-dsj-export-create-legacy-original");

        // Backfill: distribute the current export of every provider, then regenerate the Norway aggregated exports.
        from("direct:distributeAllDsjNetexExports")
                .process(this::setCorrelationIdIfMissing)
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Distributing the NeTEx exports of all providers to the legacy and new DatedServiceJourney export folders")
                .process(e -> e.getIn().setBody(getPublishedProviders()))
                .split(body())
                    .process(this::setProviderHeaders)
                    .to("direct:distributeDsjNetexExportIfPublished")
                    .to("direct:distributeDsjNetexBlocksExportIfPresent")
                .end()
                .setBody(constant(""))
                .to("direct:otp2ExportMergedNetex")
                .routeId("netex-dsj-export-distribute-all");

        // Distribute the export of every provider that is missing from the legacy or new folder (typically providers
        // that have not published since the dual export was enabled), so that aggregated exports built from these
        // folders are complete.
        from("direct:distributeMissingDsjNetexExports")
                .process(this::setCorrelationIdIfMissing)
                .process(e -> e.getIn().setBody(getPublishedProviders()))
                .split(body())
                    .process(this::setProviderHeaders)
                    .to("direct:distributeDsjNetexExportIfMissing")
                .end()
                .setBody(constant(""))
                .routeId("netex-dsj-export-distribute-missing");

        from("direct:distributeDsjNetexExportIfMissing")
                .setHeader(FILE_PREFIX).method(netexDsjExportConfig, "newExportPath")
                .to("direct:findBlob")
                .setProperty(PROP_NEW_EXPORT_EXISTS, simple("${body} != null", Boolean.class))
                .setHeader(FILE_PREFIX).method(netexDsjExportConfig, "legacyExportPath")
                .to("direct:findBlob")
                .choice()
                .when(PredicateBuilder.and(exchangeProperty(PROP_NEW_EXPORT_EXISTS).isEqualTo(true), body().isNotNull()))
                    .log(LoggingLevel.DEBUG, getClass().getName(), correlation() + "The NeTEx export of ${header." + CHOUETTE_REFERENTIAL + "} exists in the legacy and new DatedServiceJourney export folders")
                .otherwise()
                    .log(LoggingLevel.INFO, getClass().getName(), correlation() + "The NeTEx export of ${header." + CHOUETTE_REFERENTIAL + "} is missing from the legacy or new DatedServiceJourney export folder, distributing it")
                    .to("direct:distributeDsjNetexExportIfPublished")
                .end()
                .setBody(constant(""))
                .routeId("netex-dsj-export-distribute-if-missing");

        // A provider that has never published has no export in any folder: there is nothing to distribute, and
        // reporting a failed export for it on every backfill would be misleading. The export is looked up in the new
        // folder and, when it is missing there, in the default folder, which is where the export of a provider that
        // has not published since the dual export was enabled still is.
        from("direct:distributeDsjNetexExportIfPublished")
                .setHeader(FILE_PREFIX).method(netexDsjExportConfig, "newExportPath")
                .to("direct:findBlob")
                .choice()
                .when(body().isNull())
                    .setHeader(FILE_PREFIX).method(netexDsjExportConfig, "defaultExportPath")
                    .to("direct:findBlob")
                .end()
                .choice()
                .when(body().isNull())
                    .log(LoggingLevel.DEBUG, getClass().getName(), correlation() + "${header." + CHOUETTE_REFERENTIAL + "} has not published a NeTEx export yet, nothing to distribute")
                .otherwise()
                    .to("direct:distributeDsjNetexExportIgnoringFailures")
                .end()
                .setBody(constant(""))
                .routeId("netex-dsj-export-distribute-if-published");

        // An export that cannot be downgraded must not prevent the other providers from being processed.
        from("direct:distributeDsjNetexExportIgnoringFailures")
                .doTry()
                    .to("direct:distributeDsjNetexExport")
                .doCatch(Exception.class)
                    .log(LoggingLevel.WARN, getClass().getName(), correlation() + "Skipping ${header." + CHOUETTE_REFERENTIAL + "}: ${exception.message}")
                .end()
                .routeId("netex-dsj-export-distribute-ignoring-failures");
    }

    private void downgradeExport(Exchange e) throws IOException {
        File folder = new File(e.getProperty(PROP_DSJ_EXPORT_FOLDER, String.class));
        if (!folder.mkdirs() && !folder.isDirectory()) {
            throw new MardukException("Failed to create the working directory " + folder);
        }
        InputStream export = e.getIn().getBody(InputStream.class);
        if (export == null) {
            throw new MardukException("No NeTEx export found for the provider");
        }
        File downgradedExport = new File(folder, DOWNGRADED_FILE_NAME);
        NetexDsjConverter.Report report = NetexDsjConverter.downgradeZip(export, downgradedExport, folder, netexDsjExportConfig.getMaxXsltFileSizeBytes());
        log.info("Downgraded {} of {} files of the NeTEx export of {} to NeTEx 1.15", report.converted(), report.entries(), e.getIn().getHeader(CHOUETTE_REFERENTIAL));
        e.getIn().setBody(downgradedExport);
    }

    private void setLegacyOriginalDatasetHeaders(Exchange e) {
        String originalDatasetPath = e.getProperty(PROP_SAVED_TARGET_FILE_HANDLE, String.class);
        e.getIn().setHeader(FILE_HANDLE, NetexDsjExportConfig.legacyOriginalDatasetStagingPath(originalDatasetPath));
        e.getIn().setHeader(TARGET_FILE_HANDLE, NetexDsjExportConfig.legacyOriginalDatasetPath(originalDatasetPath));
    }

    private void setDefaultVariantOriginalDatasetHeaders(Exchange e) {
        String originalDatasetPath = e.getProperty(PROP_SAVED_TARGET_FILE_HANDLE, String.class);
        boolean downgraded = netexDsjExportConfig.getDefaultVariant() == NetexDsjExportConfig.Variant.LEGACY
                && netexDsjExportConfig.hasDsjReplacements(e.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class));
        e.getIn().setHeader(FILE_HANDLE, downgraded
                ? NetexDsjExportConfig.legacyOriginalDatasetStagingPath(originalDatasetPath)
                : e.getProperty(PROP_SAVED_FILE_HANDLE, String.class));
        e.getIn().setHeader(TARGET_FILE_HANDLE, originalDatasetPath);
    }

    private void setLegacyOriginalDatasetTargetHeader(Exchange e) {
        String originalDatasetPath = e.getProperty(PROP_SAVED_TARGET_FILE_HANDLE, String.class);
        e.getIn().setHeader(TARGET_FILE_HANDLE, NetexDsjExportConfig.legacyOriginalDatasetPath(originalDatasetPath));
    }

    private void rethrowCaughtException(Exchange e) throws Exception {
        throw e.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
    }

    private void setProviderHeaders(Exchange e) {
        Provider provider = e.getIn().getBody(Provider.class);
        e.getIn().setHeader(PROVIDER_ID, provider.getId());
        e.getIn().setHeader(CHOUETTE_REFERENTIAL, provider.getChouetteInfo().getReferential());
        e.getIn().setBody("");
    }

    /**
     * Providers whose dataset is published in the public bucket.
     */
    private List<Provider> getPublishedProviders() {
        return getProviderRepository().getProviders().stream()
                .filter(p -> p.getChouetteInfo().getMigrateDataToProvider() == null)
                .toList();
    }
}

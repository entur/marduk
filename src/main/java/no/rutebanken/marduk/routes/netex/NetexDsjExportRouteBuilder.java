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

import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.services.MardukPublicBlobStoreService;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static no.rutebanken.marduk.Constants.*;

/**
 * Distribute a freshly published NeTEx dataset to the three export folders used during the transition to the
 * NeTEx 1.16 DatedServiceJourney structure (see {@link NetexDsjExportConfig}):
 * the dataset exported by the import pipeline is stored in the "new" folder, the legacy copy (downgraded when
 * the dataset carries replacement information, a plain copy otherwise) is stored in the "legacy" folder, and the
 * variant configured as default is copied into the default folder.
 */
@Component
public class NetexDsjExportRouteBuilder extends BaseRouteBuilder {

    private static final String PROP_DSJ_EXPORT_FOLDER = "RutebankenDsjExportFolder";
    private static final String PROP_NEW_EXPORT_EXISTS = "RutebankenDsjNewExportExists";
    private static final String PROP_SAVED_BODY = "RutebankenDsjSavedBody";
    private static final String PROP_SAVED_FILE_HANDLE = "RutebankenDsjSavedFileHandle";
    private static final String PROP_SAVED_FILE_VERSION = "RutebankenDsjSavedFileVersion";
    private static final String PROP_SAVED_TARGET_FILE_HANDLE = "RutebankenDsjSavedTargetFileHandle";
    /**
     * Whether the distribution of one provider may fail without failing the whole batch. Only the manual backfill
     * sets it: a batch whose result is merged into an aggregated export must fail rather than skip a provider.
     */
    static final String PROP_IGNORE_DISTRIBUTION_FAILURES = "RutebankenDsjIgnoreDistributionFailures";
    /**
     * Whether the distribution reports its progress as a job event of the provider. Only the batches clear it: they
     * distribute every provider in a single exchange, so their job events would all carry the correlation id of the
     * batch, and Nabu, which aggregates job events by correlation id, would merge them into one job attributed to
     * whichever provider was distributed first. A batch is a system action that belongs to no provider's import job.
     */
    static final String PROP_REPORT_JOB_EVENTS = "RutebankenDsjReportJobEvents";
    /**
     * The state reported by direct:reportDsjNetexExportJobEvent.
     */
    private static final String PROP_JOB_EVENT_STATE = "RutebankenDsjJobEventState";
    private static final String DOWNGRADED_FILE_NAME = "legacy.zip";
    /**
     * Reported as the user of the job events of an export that no user triggered, typically an aggregated export
     * distributing the provider exports that are missing from the legacy or new folder.
     */
    private static final String SYSTEM_USERNAME = "System";

    private final NetexDsjExportConfig netexDsjExportConfig;

    private final MardukPublicBlobStoreService mardukPublicBlobStoreService;

    public NetexDsjExportRouteBuilder(NetexDsjExportConfig netexDsjExportConfig,
                                      MardukPublicBlobStoreService mardukPublicBlobStoreService) {
        this.netexDsjExportConfig = netexDsjExportConfig;
        this.mardukPublicBlobStoreService = mardukPublicBlobStoreService;
    }

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:distributeDsjNetexExport")
                .validate(header(CHOUETTE_REFERENTIAL).isNotNull())
                .process(this::defaultUsernameToSystem)
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Distributing the NeTEx export of ${header." + CHOUETTE_REFERENTIAL + "} to the legacy and new DatedServiceJourney export folders")
                .setProperty(PROP_JOB_EVENT_STATE, constant(JobEvent.State.STARTED))
                .to("direct:reportDsjNetexExportJobEvent")
                .setProperty(PROP_DSJ_EXPORT_FOLDER, simple(netexDsjExportConfig.getDownloadDirectory() + "/${header." + CORRELATION_ID + "}_${date:now:yyyyMMddHHmmssSSS}"))
                .doTry()
                    .to("direct:seedDsjNewNetexExportIfMissing")
                    .to("direct:createLegacyDsjNetexExport")
                    .setHeader(FILE_HANDLE).method(netexDsjExportConfig, "defaultVariantSourcePath")
                    .setHeader(TARGET_FILE_HANDLE).method(netexDsjExportConfig, "defaultExportPath")
                    .to("direct:copyBlobInBucket")
                    .setProperty(PROP_JOB_EVENT_STATE, constant(JobEvent.State.OK))
                    .to("direct:reportDsjNetexExportJobEvent")
                .doCatch(Exception.class)
                    .log(LoggingLevel.ERROR, getClass().getName(), correlation() + "Failed to distribute the NeTEx export of ${header." + CHOUETTE_REFERENTIAL + "}: ${exception.message}")
                    .setProperty(PROP_JOB_EVENT_STATE, constant(JobEvent.State.FAILED))
                    .to("direct:reportDsjNetexExportJobEvent")
                    // the default folder must not diverge from the other folders: fail the publication so that it is retried
                    .process(this::rethrowCaughtException)
                .doFinally()
                    .process(e -> deleteDirectoryRecursively(e.getProperty(PROP_DSJ_EXPORT_FOLDER, String.class)))
                .end()
                .setBody(constant(""))
                .routeId("netex-dsj-export-distribute");

        // Report the state carried by PROP_JOB_EVENT_STATE as an EXPORT_NETEX_LEGACY job event of the provider being
        // distributed, unless the distribution runs as part of a batch (see PROP_REPORT_JOB_EVENTS).
        // In its own route so that the filter is not nested in the doTry() of the calling routes, and so that the
        // event is not even built when it is not reported: JobEvent.build() serialises the event into the body, and a
        // body that direct:updateStatus never publishes would be left behind for the next step to trip over.
        from("direct:reportDsjNetexExportJobEvent")
                .filter(this::reportsJobEvents)
                    .process(e -> JobEvent.providerJobBuilder(e)
                            .timetableAction(JobEvent.TimetableAction.EXPORT_NETEX_LEGACY)
                            .state(e.getProperty(PROP_JOB_EVENT_STATE, JobEvent.State.class))
                            .build())
                    .to("direct:updateStatus")
                .end()
                .routeId("netex-dsj-export-report-job-event");

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

        // Private export (NeTEx with blocks): store a legacy copy of the blocks export next to the one produced by
        // the pipeline. Called after every publication of the blocks export. The legacy copy is overwritten in place,
        // so a failure fails the calling step and is retried; swallowing it would leave the previous legacy copy next
        // to a freshly published blocks export. Only the manual backfill tolerates it, so that one provider that
        // cannot be downgraded does not stop the others (see PROP_IGNORE_DISTRIBUTION_FAILURES).
        from("direct:distributeDsjNetexBlocksExport")
                .filter(constant(netexDsjExportConfig.isEnabled()))
                .to("direct:doDistributeDsjNetexBlocksExport")
                .end()
                .routeId("netex-dsj-export-distribute-blocks");

        from("direct:doDistributeDsjNetexBlocksExport")
                .validate(header(CHOUETTE_REFERENTIAL).isNotNull())
                .process(this::defaultUsernameToSystem)
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Downgrading the NeTEx blocks export of ${header." + CHOUETTE_REFERENTIAL + "} to NeTEx 1.15")
                .setProperty(PROP_SAVED_BODY, body())
                .setProperty(PROP_SAVED_FILE_HANDLE, header(FILE_HANDLE))
                .setProperty(PROP_DSJ_EXPORT_FOLDER, simple(netexDsjExportConfig.getDownloadDirectory() + "/${header." + CORRELATION_ID + "}_${date:now:yyyyMMddHHmmssSSS}"))
                .doTry()
                    .to("direct:createLegacyDsjNetexBlocksExport")
                .doCatch(Exception.class)
                    .log(LoggingLevel.ERROR, getClass().getName(), correlation() + "Failed to downgrade the NeTEx blocks export of ${header." + CHOUETTE_REFERENTIAL + "}: ${exception.message}")
                    .setProperty(PROP_JOB_EVENT_STATE, constant(JobEvent.State.FAILED))
                    .to("direct:reportDsjNetexExportJobEvent")
                    // the timetable API cannot tell a stale legacy copy from a current one: fail the publication so
                    // that it is retried, rather than serving the previous copy as the current dataset.
                    .process(this::rethrowUnlessDistributionFailuresIgnored)
                .doFinally()
                    .process(e -> deleteDirectoryRecursively(e.getProperty(PROP_DSJ_EXPORT_FOLDER, String.class)))
                    .setHeader(FILE_HANDLE, exchangeProperty(PROP_SAVED_FILE_HANDLE))
                    .setBody(exchangeProperty(PROP_SAVED_BODY))
                    .removeProperty(PROP_SAVED_BODY)
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
        // This route writes the legacy copy in imported-dsj-legacy and the variant selected by
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
                .setProperty(PROP_SAVED_BODY, body())
                .setProperty(PROP_SAVED_FILE_HANDLE, header(FILE_HANDLE))
                .setProperty(PROP_SAVED_TARGET_FILE_HANDLE, header(TARGET_FILE_HANDLE))
                .setProperty(PROP_SAVED_FILE_VERSION, header(FILE_VERSION))
                .setProperty(PROP_DSJ_EXPORT_FOLDER, simple(netexDsjExportConfig.getDownloadDirectory() + "/${header." + CORRELATION_ID + "}_${date:now:yyyyMMddHHmmssSSS}"))
                .doTry()
                    .to("direct:createLegacyOriginalDataset")
                    .to("direct:copyDefaultVariantOriginalDatasetToNisaba")
                .doCatch(Exception.class)
                    .log(LoggingLevel.ERROR, getClass().getName(), correlation() + "Failed to store the original dataset of ${header." + CHOUETTE_REFERENTIAL + "} in the DatedServiceJourney folders of Nisaba: ${exception.message}")
                    // the default folder receives the dataset from this route only: swallowing the failure would
                    // leave the import without an original dataset in Nisaba, silently and for good. Fail the
                    // calling step instead so that it is retried.
                    .process(this::rethrowCaughtException)
                .doFinally()
                    .process(e -> deleteDirectoryRecursively(e.getProperty(PROP_DSJ_EXPORT_FOLDER, String.class)))
                    .setHeader(FILE_HANDLE, exchangeProperty(PROP_SAVED_FILE_HANDLE))
                    .setHeader(TARGET_FILE_HANDLE, exchangeProperty(PROP_SAVED_TARGET_FILE_HANDLE))
                    // direct:uploadInternalBlob overwrites FILE_VERSION with the generation of the staged copy
                    .setHeader(FILE_VERSION, exchangeProperty(PROP_SAVED_FILE_VERSION))
                    // this route is a side step: it must leave the exchange as it found it. The caller may carry a
                    // serialised JobEvent in the body, which direct:updateStatus publishes and nothing else.
                    .setBody(exchangeProperty(PROP_SAVED_BODY))
                    .removeProperty(PROP_SAVED_BODY)
                .end()
                .routeId("netex-dsj-export-do-distribute-original-to-nisaba");

        // The default folder receives the variant selected by netex.export.dsj.default.variant: the legacy copy
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
                // an export that cannot be distributed must not prevent the other providers from being processed
                .setProperty(PROP_IGNORE_DISTRIBUTION_FAILURES, constant(true))
                // the whole batch runs in one exchange: its job events would be aggregated into a single job (see
                // PROP_REPORT_JOB_EVENTS)
                .setProperty(PROP_REPORT_JOB_EVENTS, constant(false))
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
                // an aggregated export built from a folder from which a provider export is missing would silently
                // lose that provider: a provider that cannot be distributed fails the aggregated export, which is
                // then retried, rather than being skipped.
                .setProperty(PROP_IGNORE_DISTRIBUTION_FAILURES, constant(false))
                // the whole batch runs in the exchange of the aggregated export: its job events would be aggregated
                // into a single job (see PROP_REPORT_JOB_EVENTS)
                .setProperty(PROP_REPORT_JOB_EVENTS, constant(false))
                .process(e -> e.getIn().setBody(getProvidersMissingFromAVariantFolder()))
                .split(body()).stopOnException()
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

        // An export that cannot be downgraded must not prevent the other providers from being processed, as long as
        // the caller does not build an aggregated export out of the folders being filled (see
        // PROP_IGNORE_DISTRIBUTION_FAILURES).
        from("direct:distributeDsjNetexExportIgnoringFailures")
                .doTry()
                    .to("direct:distributeDsjNetexExport")
                .doCatch(Exception.class)
                    .log(LoggingLevel.WARN, getClass().getName(), correlation() + "Failed to distribute the NeTEx export of ${header." + CHOUETTE_REFERENTIAL + "}: ${exception.message}")
                    .process(this::rethrowUnlessDistributionFailuresIgnored)
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

    /**
     * Whether the distribution reports its progress as a job event of the provider, true unless a batch cleared it
     * (see {@link #PROP_REPORT_JOB_EVENTS}).
     */
    private boolean reportsJobEvents(Exchange e) {
        return e.getProperty(PROP_REPORT_JOB_EVENTS, true, Boolean.class);
    }

    private void rethrowUnlessDistributionFailuresIgnored(Exchange e) throws Exception {
        if (!e.getProperty(PROP_IGNORE_DISTRIBUTION_FAILURES, false, Boolean.class)) {
            rethrowCaughtException(e);
        }
    }

    /**
     * Report the job events of an export that no user triggered as triggered by the system, the way the other
     * routes triggered by the pipeline itself do: a job event without a username is rendered as an empty entry.
     */
    private void defaultUsernameToSystem(Exchange e) {
        if (e.getIn().getHeader(USERNAME) == null) {
            e.getIn().setHeader(USERNAME, SYSTEM_USERNAME);
        }
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

    /**
     * The published providers whose export is missing from the legacy or the new DatedServiceJourney export folder.
     * <p>
     * Both folders are listed once and the providers are matched against the two listings in memory. Looking every
     * provider up individually, as the routes downstream still do for the few providers returned here, is two
     * blob store requests per provider on every aggregated export, almost always only to find that nothing is
     * missing.
     */
    private List<Provider> getProvidersMissingFromAVariantFolder() {
        List<Provider> providers = getPublishedProviders();
        List<String> fileNames = providers.stream()
                .map(p -> NetexDsjExportConfig.aggregatedNetexFileName(p.getChouetteInfo().getReferential()))
                .toList();
        Map<String, Long> newExports = countExportsByFileName(netexDsjExportConfig.getNewBlobPath(), fileNames);
        Map<String, Long> legacyExports = countExportsByFileName(netexDsjExportConfig.getLegacyBlobPath(), fileNames);
        List<Provider> missing = providers.stream()
                .filter(p -> !isPresentExactlyOnce(newExports, p, netexDsjExportConfig.getNewBlobPath())
                        || !isPresentExactlyOnce(legacyExports, p, netexDsjExportConfig.getLegacyBlobPath()))
                .toList();
        log.info("Checked {} providers against the DatedServiceJourney export folders, {} need to be distributed",
                providers.size(), missing.size());
        return missing;
    }

    /**
     * The blobs of a folder, counted by the export file name they start with. A file name is only a prefix of the
     * blob name, as {@link no.rutebanken.marduk.services.AbstractBlobStoreService#findBlob(String)} treats it.
     */
    private Map<String, Long> countExportsByFileName(String folder, List<String> fileNames) {
        Map<String, Long> counts = new HashMap<>();
        for (BlobStoreFiles.File file : mardukPublicBlobStoreService.listBlobsFlatInFolder(folder).getFiles()) {
            for (String fileName : fileNames) {
                if (file.getName().startsWith(fileName)) {
                    counts.merge(fileName, 1L, Long::sum);
                }
            }
        }
        return counts;
    }

    /**
     * Reproduces what {@code direct:findBlob} does for a single provider: absent means the export has to be
     * distributed, and several matches is an error, because the folder then holds an export that cannot be
     * identified.
     */
    private boolean isPresentExactlyOnce(Map<String, Long> exports, Provider provider, String folder) {
        String fileName = NetexDsjExportConfig.aggregatedNetexFileName(provider.getChouetteInfo().getReferential());
        long count = exports.getOrDefault(fileName, 0L);
        if (count > 1) {
            throw new MardukException("Found multiple files matching the prefix " + folder + fileName);
        }
        return count == 1;
    }
}

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

import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.FileType;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static no.rutebanken.marduk.Constants.*;

/**
 * Upgrade the pre-1.16 NeTEx files of an uploaded dataset to NeTEx 1.16 once the import pipeline works with NeTEx 1.16.
 * <p>
 * Only the datasets of the codespaces whose DatedServiceJourneys carry replacement information
 * (see {@link NetexDsjExportConfig#hasDsjReplacements(String)}) are concerned: the other datasets are accepted as is.
 * The upgraded dataset replaces the uploaded file in the internal bucket, so that the rest of the pipeline
 * (pre-validation, import, export) works on a NeTEx 1.16 dataset.
 */
@Component
public class NetexDsjUpgradeRouteBuilder extends BaseRouteBuilder {

    private static final String PROP_DSJ_UPGRADE_FOLDER = "RutebankenDsjUpgradeFolder";
    private static final String PROP_DSJ_UPGRADED = "RutebankenDsjUpgraded";
    private static final String UPGRADED_FILE_NAME = "upgraded.zip";

    private final NetexDsjExportConfig netexDsjExportConfig;

    public NetexDsjUpgradeRouteBuilder(NetexDsjExportConfig netexDsjExportConfig) {
        this.netexDsjExportConfig = netexDsjExportConfig;
    }

    @Override
    public void configure() throws Exception {
        super.configure();

        // Called after the classification of an uploaded file, with FILE_HANDLE pointing to the file in the
        // internal bucket. No-op unless the upgrade is enabled, the file is a NeTEx dataset and its codespace
        // produces DatedServiceJourney replacement information.
        from("direct:upgradeNetexDatasetIfNeeded")
                .filter(this::requiresUpgradeCheck)
                .to("direct:upgradeNetexDataset")
                .end()
                .routeId("netex-dsj-upgrade-if-needed");

        from("direct:upgradeNetexDataset")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Checking ${header." + FILE_HANDLE + "} for NeTEx files to upgrade to NeTEx 1.16")
                .setProperty(PROP_DSJ_UPGRADE_FOLDER, simple(netexDsjExportConfig.getDownloadDirectory() + "/upgrade_${header." + CORRELATION_ID + "}_${date:now:yyyyMMddHHmmssSSS}"))
                .doTry()
                    .to("direct:getInternalBlob")
                    .process(this::upgradeDataset)
                    .to("direct:replaceUpgradedNetexDataset")
                .doFinally()
                    .process(e -> deleteDirectoryRecursively(e.getProperty(PROP_DSJ_UPGRADE_FOLDER, String.class)))
                    .setBody(constant(""))
                .end()
                .routeId("netex-dsj-upgrade");

        // Sub-route to avoid a filter() nested in the doTry() of the calling route.
        from("direct:replaceUpgradedNetexDataset")
                .filter(exchangeProperty(PROP_DSJ_UPGRADED).isEqualTo(true))
                    .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Replacing ${header." + FILE_HANDLE + "} with the dataset upgraded to NeTEx 1.16")
                    .to("direct:uploadInternalBlob")
                .end()
                .routeId("netex-dsj-upgrade-replace");
    }

    private boolean requiresUpgradeCheck(Exchange e) {
        return netexDsjExportConfig.isUpgradeEnabled()
                && FileType.NETEXPROFILE.name().equals(e.getIn().getHeader(FILE_TYPE, String.class))
                && netexDsjExportConfig.hasDsjReplacements(referentialFor(e));
    }

    /**
     * The referential of the uploaded file: the CHOUETTE_REFERENTIAL header when present (files uploaded through
     * the HTTP endpoints), otherwise the referential of the provider identified by the PROVIDER_ID header.
     */
    private String referentialFor(Exchange e) {
        String referential = e.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class);
        if (referential != null) {
            return referential;
        }
        Long providerId = e.getIn().getHeader(PROVIDER_ID, Long.class);
        return providerId == null ? null : getProviderRepository().getReferential(providerId);
    }

    /**
     * Upgrade the pre-1.16 NeTEx files of the dataset. The body is replaced by the upgraded archive when at least one
     * file was upgraded, and emptied otherwise.
     */
    private void upgradeDataset(Exchange e) throws IOException {
        InputStream dataset = e.getIn().getBody(InputStream.class);
        if (dataset == null) {
            throw new MardukException("File not found in the blob store: " + e.getIn().getHeader(FILE_HANDLE));
        }
        File folder = new File(e.getProperty(PROP_DSJ_UPGRADE_FOLDER, String.class));
        if (!folder.mkdirs() && !folder.isDirectory()) {
            throw new MardukException("Failed to create the working directory " + folder);
        }
        File upgradedDataset = new File(folder, UPGRADED_FILE_NAME);
        NetexDsjConverter.Report report = NetexDsjConverter.upgradeZip(dataset, upgradedDataset, folder, netexDsjExportConfig.getMaxXsltFileSizeBytes());
        if (report.converted() > 0) {
            log.info("Upgraded {} of {} files of {} to NeTEx 1.16", report.converted(), report.entries(), e.getIn().getHeader(FILE_HANDLE));
            e.setProperty(PROP_DSJ_UPGRADED, true);
            e.getIn().setBody(upgradedDataset);
        } else {
            log.info("No NeTEx file older than 1.16 found in {}, the dataset is kept as is", e.getIn().getHeader(FILE_HANDLE));
            e.setProperty(PROP_DSJ_UPGRADED, false);
            e.getIn().setBody("");
        }
    }
}

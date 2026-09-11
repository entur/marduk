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

import no.rutebanken.marduk.Constants;
import org.apache.camel.Header;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;
import static no.rutebanken.marduk.Constants.CURRENT_AGGREGATED_NETEX_FILENAME;

/**
 * Configuration of the dual NeTEx export produced during the transition to the NeTEx 1.16 DatedServiceJourney
 * structure (netex-java-model 2.0.16).
 * <p>
 * When enabled, each published dataset is stored in three folders of the public bucket:
 * <ul>
 *     <li>{@code netex-dsj-new}: the dataset as exported by the import pipeline (NeTEx 1.16);</li>
 *     <li>{@code netex-dsj-legacy}: the dataset downgraded to NeTEx 1.15;</li>
 *     <li>the default folder {@code netex}: a copy of one of the two variants, selected by
 *     {@code netex.export.dsj.default.variant}.</li>
 * </ul>
 * When disabled, the dataset is published directly in the default folder as before.
 */
@Component
public class NetexDsjExportConfig {

    public static final String DEFAULT_EXPORT_PATH = BLOBSTORE_PATH_OUTBOUND + "netex/";

    /**
     * The two variants of a published dataset.
     */
    public enum Variant {
        /**
         * NeTEx 1.15, produced by downgrading the exported dataset.
         */
        LEGACY,
        /**
         * NeTEx 1.16, the dataset as exported by the import pipeline.
         */
        NEW
    }

    @Value("${netex.export.dsj.enabled:false}")
    private boolean enabled;

    @Value("${netex.export.dsj.default.variant:legacy}")
    private String defaultVariant;

    @Value("${netex.export.dsj.api.default.variant:legacy}")
    private String apiDefaultVariant;

    @Value("${netex.export.dsj.legacy.blob.path:outbound/netex-dsj-legacy/}")
    private String legacyBlobPath;

    @Value("${netex.export.dsj.new.blob.path:outbound/netex-dsj-new/}")
    private String newBlobPath;

    @Value("${netex.export.dsj.download.directory:files/netex/dsj}")
    private String downloadDirectory;

    @Value("${netex.export.dsj.xslt.max.file.size.mb:300}")
    private long maxXsltFileSizeMb;

    /**
     * Codespaces whose datasets contain DatedServiceJourneys with replacement information, the only construct that
     * differs between NeTEx 1.15 and NeTEx 1.16. Only their datasets are converted (downgraded on export, upgraded
     * on import); the datasets of the other codespaces are used as is.
     */
    @Value("${netex.dsj.codespaces:VYG,GOA,SJN}")
    private List<String> dsjCodespaces;

    /**
     * Whether uploaded NeTEx 1.15 datasets are upgraded to NeTEx 1.16. To enable once the import pipeline works
     * with NeTEx 1.16.
     */
    @Value("${netex.import.dsj.upgrade.enabled:false}")
    private boolean upgradeEnabled;

    public boolean isEnabled() {
        return enabled;
    }

    public Variant getDefaultVariant() {
        return Variant.valueOf(defaultVariant.trim().toUpperCase());
    }

    /**
     * Variant served by the timetable API when the request does not specify one.
     */
    public Variant getApiDefaultVariant() {
        return Variant.valueOf(apiDefaultVariant.trim().toUpperCase());
    }

    /**
     * Resolve the {@code dsjcompatibility} request parameter of the timetable API.
     *
     * @param dsjCompatibility the request parameter, {@code legacy} or {@code new}; null or blank for the default variant.
     * @throws IllegalArgumentException if the parameter has another value.
     */
    public Variant resolveApiVariant(String dsjCompatibility) {
        if (dsjCompatibility == null || dsjCompatibility.isBlank()) {
            return getApiDefaultVariant();
        }
        try {
            return Variant.valueOf(dsjCompatibility.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid value for parameter dsjcompatibility: '" + dsjCompatibility + "', expected legacy or new");
        }
    }

    public String getLegacyBlobPath() {
        return legacyBlobPath;
    }

    public String getNewBlobPath() {
        return newBlobPath;
    }

    public String getDownloadDirectory() {
        return downloadDirectory;
    }

    public long getMaxXsltFileSizeBytes() {
        return maxXsltFileSizeMb * 1024 * 1024;
    }

    public boolean isUpgradeEnabled() {
        return upgradeEnabled;
    }

    /**
     * Whether the datasets of the given referential (with or without the {@code rb_} prefix) contain
     * DatedServiceJourneys with replacement information and must therefore be converted between NeTEx 1.15 and
     * NeTEx 1.16 with the XSLT transformation. The datasets of the other codespaces are used as is.
     */
    public boolean hasDsjReplacements(@Header(Constants.CHOUETTE_REFERENTIAL) String referential) {
        if (referential == null) {
            return false;
        }
        String codespace = referential.toLowerCase(Locale.ROOT);
        if (codespace.startsWith("rb_")) {
            codespace = codespace.substring(3);
        }
        for (String dsjCodespace : dsjCodespaces) {
            if (dsjCodespace.trim().equalsIgnoreCase(codespace)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Path of the dataset in the default folder ({@code outbound/netex/rb_xxx-aggregated-netex.zip}).
     */
    public String defaultExportPath(@Header(Constants.CHOUETTE_REFERENTIAL) String referential) {
        return DEFAULT_EXPORT_PATH + aggregatedNetexFileName(referential);
    }

    /**
     * Path of the NeTEx 1.16 variant of the dataset.
     */
    public String newExportPath(@Header(Constants.CHOUETTE_REFERENTIAL) String referential) {
        return newBlobPath + aggregatedNetexFileName(referential);
    }

    /**
     * Path of the NeTEx 1.15 variant of the dataset.
     */
    public String legacyExportPath(@Header(Constants.CHOUETTE_REFERENTIAL) String referential) {
        return legacyBlobPath + aggregatedNetexFileName(referential);
    }

    /**
     * Path where the import pipeline stores the dataset it has just exported: the {@code netex-dsj-new} folder
     * when the dual export is enabled, otherwise directly the default folder.
     */
    public String publicationTargetPath(@Header(Constants.CHOUETTE_REFERENTIAL) String referential) {
        return enabled ? newExportPath(referential) : defaultExportPath(referential);
    }

    /**
     * Path of the variant that must be copied into the default folder.
     */
    public String defaultVariantSourcePath(@Header(Constants.CHOUETTE_REFERENTIAL) String referential) {
        return variantPath(getDefaultVariant(), aggregatedNetexFileName(referential));
    }

    /**
     * Folder holding the given variant of the exports.
     */
    public String variantFolder(Variant variant) {
        return variant == Variant.LEGACY ? legacyBlobPath : newBlobPath;
    }

    /**
     * Path of the given variant of a file (a per-provider export or the Norway aggregated export).
     */
    public String variantPath(Variant variant, String fileName) {
        return variantFolder(variant) + fileName;
    }

    /**
     * Path in the internal bucket of the NeTEx blocks export as produced by the pipeline (NeTEx 1.16 after the migration).
     */
    public String blocksExportPath(@Header(Constants.CHOUETTE_REFERENTIAL) String referential) {
        return Constants.BLOBSTORE_PATH_NETEX_BLOCKS_EXPORT + aggregatedNetexFileName(referential);
    }

    /**
     * Path in the internal bucket of the NeTEx 1.15 copy of the NeTEx blocks export.
     */
    public String legacyBlocksExportPath(@Header(Constants.CHOUETTE_REFERENTIAL) String referential) {
        return Constants.BLOBSTORE_PATH_NETEX_BLOCKS_EXPORT_DSJ_LEGACY + aggregatedNetexFileName(referential);
    }

    public String blocksExportPath(Variant variant, String referential) {
        return variant == Variant.LEGACY ? legacyBlocksExportPath(referential) : blocksExportPath(referential);
    }

    /**
     * Path in the Nisaba bucket of the NeTEx 1.15 copy of an original dataset, given the path of the original dataset
     * in the default folder ({@code imported/<referential>/<referential>_<timestamp>.zip}).
     */
    public static String legacyOriginalDatasetPath(String originalDatasetPath) {
        return originalDatasetPathIn(Constants.BLOBSTORE_PATH_NISABA_IMPORTED_DSJ_LEGACY, originalDatasetPath);
    }

    /**
     * Path in the Nisaba bucket of the original dataset as uploaded (NeTEx 1.16 after the upgrade).
     */
    public static String newOriginalDatasetPath(String originalDatasetPath) {
        return originalDatasetPathIn(Constants.BLOBSTORE_PATH_NISABA_IMPORTED_DSJ_NEW, originalDatasetPath);
    }

    /**
     * Path in Nisaba where the import pipeline stores the dataset uploaded by the provider: the
     * {@code imported-dsj-new} folder when the dual export is enabled, otherwise directly the default folder, which
     * then receives the variant selected by {@code netex.export.dsj.default.variant}.
     *
     * @param originalDatasetPath the path of the original dataset in the default folder.
     */
    public String originalDatasetPublicationPath(@Header(Constants.TARGET_FILE_HANDLE) String originalDatasetPath) {
        return enabled ? newOriginalDatasetPath(originalDatasetPath) : originalDatasetPath;
    }

    private static String originalDatasetPathIn(String folder, String originalDatasetPath) {
        if (!originalDatasetPath.startsWith(Constants.BLOBSTORE_PATH_NISABA_IMPORTED)) {
            throw new IllegalArgumentException("Unexpected path of the original dataset in Nisaba: " + originalDatasetPath);
        }
        return folder + originalDatasetPath.substring(Constants.BLOBSTORE_PATH_NISABA_IMPORTED.length());
    }

    /**
     * Staging path in the internal bucket of the NeTEx 1.15 copy of an original dataset.
     */
    public static String legacyOriginalDatasetStagingPath(String originalDatasetPath) {
        return originalDatasetPathIn(Constants.BLOBSTORE_PATH_DSJ_LEGACY_ORIGINAL_DATASET, originalDatasetPath);
    }

    public static String aggregatedNetexFileName(String referential) {
        return referential + "-" + CURRENT_AGGREGATED_NETEX_FILENAME;
    }
}

package no.rutebanken.marduk.routes.experimental;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.pipeline.MardukMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.Constants.FOLDER_NAME;

/**
 * Note:
 * When using experimental imports, all paths must include correlation ID to ensure isolation between parallel imports.
 * This is essential when using experimental imports to ensure consistency when merging and validating timetable data with flexible lines.
 * This is not needed for Chouette imports, because of Chouette lock mechanism based on referential name.
 * */
@Component
public class ExperimentalImportHelpers {
    private final boolean experimentalImportEnabled;
    private final ProviderRepository providerRepository;

    private static final String UNPACKED_WITH_FLEXIBLE_LINES_SUB_FOLDER = "/unpacked-with-flexible-lines";
    private static final String MERGED_NETEX_SUB_FOLDER = "/result";

    public ExperimentalImportHelpers(
        @Value("${marduk.experimental-import.enabled:false}") boolean experimentalImportEnabled,
        @Autowired ProviderRepository providerRepository
    ) {
        this.experimentalImportEnabled = experimentalImportEnabled;
        this.providerRepository = providerRepository;
    }

    private Provider getProvider(String referential) {
        return providerRepository
                .getProviders()
                .stream()
                .filter(provider -> referential.equalsIgnoreCase((provider.getChouetteInfo().getReferential()))).findFirst().orElse(null);
    }

    public boolean shouldRunExperimentalImport(MardukMessage message) {
        if (experimentalImportEnabled) {
            String referential = datasetReferentialFor(message);
            if (referential.startsWith("rb_")) {
                referential = referential.replace("rb_", "");
            }
            Provider provider = getProvider(referential);
            if (provider == null) {
                return false;
            }
            return provider.getChouetteInfo().hasEnabledExperimentalImport();
        }
        return false;
    }

    /**
     * Returns true when the provider has explicitly configured an empty set of service link modes,
     * meaning no service links should be generated and the servicelinker step can be skipped entirely.
     * Returns false when modes is null (not yet configured, so servicelinker runs for all modes)
     * or when one or more modes are present.
     */
    public boolean shouldSkipServicelinker(MardukMessage message) {
        String referential = datasetReferentialFor(message);
        if (referential.startsWith("rb_")) {
            referential = referential.replace("rb_", "");
        }
        Provider provider = getProvider(referential);
        Set<String> modes = provider.getChouetteInfo().getGenerateMissingServiceLinksForModes();
        return modes != null && modes.isEmpty();
    }

    /**
     * Sets the ServiceLinkModes header on the message based on the provider's configured transport modes.
     * If generateMissingServiceLinksForModes is null (not yet configured), the header is not set and
     * servicelinker will generate links for all modes (backward-compatible behaviour).
     * If one or more modes are present, the header is serialised as a comma-separated string and
     * servicelinker will restrict generation to those modes.
     * Note: the empty-set case (no service links at all) is handled upstream by shouldSkipServicelinker(),
     * so this method is only called when modes is null or non-empty.
     */
    public void setServiceLinkModesHeader(MardukMessage message) {
        String referential = datasetReferentialFor(message);
        if (referential.startsWith("rb_")) {
            referential = referential.replace("rb_", "");
        }
        Provider provider = getProvider(referential);
        Set<String> modes = provider.getChouetteInfo().getGenerateMissingServiceLinksForModes();
        if (modes != null) {
            message.setHeader(Constants.SERVICE_LINK_MODES_HEADER, String.join(",", modes));
        }
    }

    public String pathToNetexForServicelinker(MardukMessage message) {
        String referential = datasetReferentialFor(message);
        return "servicelinker/" + referential + "/" + correlationIdFor(message) + "/" + referential + "-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME;
    }

    /**
     * Returns the path to the NeTEx file in the exchange bucket for Ashur filtering.
     * The path includes correlation ID to ensure isolation between parallel imports of the same codespace.
     */
    public String pathToNetexForAshurFiltering(MardukMessage message) {
        String referential = chouetteReferentialFor(message);
        return BLOBSTORE_PATH_OUTBOUND + "netex/" + referential + "/" + correlationIdFor(message) + "/" + referential + "-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME;
    }

    public String pathToNetexExportFromChouetteToMergeWithFlex(MardukMessage message) {
        return BLOBSTORE_PATH_CHOUETTE + "netex/" + chouetteReferentialFor(message) + "-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME;
    }

    public String pathToNetexFromAshurToMergeWithFlex(MardukMessage message) {
        String referential = chouetteReferentialFor(message);
        return "filtered-netex/" + referential + "/netex-before-merging/" + correlationIdFor(message) + "/" + referential + "-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME;
    }

    /**
     * Stable per-referential path holding the most recent Ashur StandardImportFilter output
     * (i.e. the without-blocks ordinary NeTEx) for the codespace.
     * Used as a fallback when the merge is triggered from a different correlation (e.g. after a FLEX
     * post-validation) and the correlation-keyed path from {@link #pathToNetexFromAshurToMergeWithFlex}
     * does not contain a matching file. Must NOT receive the with-blocks output.
     */
    public String pathToLatestNetexWithoutBlocksFromAshur(MardukMessage message) {
        String referential = chouetteReferentialFor(message);
        return "filtered-netex/" + referential + "/latest-without-blocks/" + referential + "-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME;
    }

    public String pathToNetexWithoutBlocksProducedByAshur(MardukMessage message) {
        String referential = chouetteReferentialFor(message);
        return "filtered-netex/" + referential + "/" + correlationIdFor(message) + "/" + referential + "-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME;
    }

    public String pathToNetexWithBlocksProducedByAshur(MardukMessage message) {
        String referential = chouetteReferentialFor(message);
        return "filtered-netex/" + referential + "/blocks/" + correlationIdFor(message) + "/" + referential + "-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME;
    }

    /**
     * Returns the path to the pre-filtering NeTEx file saved for block export.
     * This is the file that was used as input to Ashur standard filtering - either the servicelinker output
     * (if servicelinker ran) or the original file. It is saved at a deterministic internal path so that
     * the block export can reference it later, after the standard filtering and post-validation hops.
     */
    public String pathToPreFilteringNetexForBlockExport(MardukMessage message) {
        String referential = chouetteReferentialFor(message);
        return "filtered-netex/" + referential + "/block-export-input/" + correlationIdFor(message) + "/" + referential + "-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME;
    }

    /**
     * Returns the path to the NeTEx file with blocks in the exchange bucket for Ashur filtering.
     * The path includes correlation ID to ensure isolation between parallel imports of the same codespace.
     */
    public String pathToNetexWithBlocksForAshurFiltering(MardukMessage message) {
        String referential = chouetteReferentialFor(message);
        return BLOBSTORE_PATH_OUTBOUND + "netex/" + referential + "/" + correlationIdFor(message) + "/" + referential + "-" + Constants.CURRENT_NETEX_WITH_BLOCKS_FILENAME;
    }

    public String pathToExportedNetexFileToMergeWithFlex(MardukMessage message) {
        if (shouldRunExperimentalImport(message)) {
            return pathToNetexFromAshurToMergeWithFlex(message);
        }
        return pathToNetexExportFromChouetteToMergeWithFlex(message);
    }

    public String flexibleDataWorkingDirectory(MardukMessage message) {
        if (shouldRunExperimentalImport(message)) {
            return message.getProperty(FOLDER_NAME, String.class) + "/" + correlationIdFor(message) + UNPACKED_WITH_FLEXIBLE_LINES_SUB_FOLDER;
        }
        return message.getProperty(FOLDER_NAME, String.class) + UNPACKED_WITH_FLEXIBLE_LINES_SUB_FOLDER;
    }

    public String directoryForMergedNetex(MardukMessage message) {
        if (shouldRunExperimentalImport(message)) {
            return message.getProperty(FOLDER_NAME, String.class) + "/" + correlationIdFor(message) + MERGED_NETEX_SUB_FOLDER;
        }
        return message.getProperty(FOLDER_NAME, String.class) + MERGED_NETEX_SUB_FOLDER;
    }

    private String correlationIdFor(MardukMessage message) {
        return message.getHeader(Constants.CORRELATION_ID, String.class);
    }

    private String datasetReferentialFor(MardukMessage message) {
        return message.getHeader(Constants.DATASET_REFERENTIAL, String.class);
    }

    private String chouetteReferentialFor(MardukMessage message) {
        return message.getHeader(Constants.CHOUETTE_REFERENTIAL, String.class);
    }
}

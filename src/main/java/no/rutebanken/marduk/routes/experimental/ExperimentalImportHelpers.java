package no.rutebanken.marduk.routes.experimental;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.ProviderRepository;
import org.apache.camel.Exchange;
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

    public boolean shouldRunExperimentalImport(Exchange exchange) {
        if (experimentalImportEnabled) {
            String referential = datasetReferentialFor(exchange);
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
     * Returns false when modes is null (not yet configured — servicelinker runs for all modes)
     * or when one or more modes are present.
     */
    public boolean shouldSkipServicelinker(Exchange exchange) {
        String referential = datasetReferentialFor(exchange);
        if (referential.startsWith("rb_")) {
            referential = referential.replace("rb_", "");
        }
        Provider provider = getProvider(referential);
        Set<String> modes = provider.getChouetteInfo().getGenerateMissingServiceLinksForModes();
        return modes != null && modes.isEmpty();
    }

    /**
     * Sets the ServiceLinkModes header on the exchange based on the provider's configured transport modes.
     * If generateMissingServiceLinksForModes is null (not yet configured), the header is not set and
     * servicelinker will generate links for all modes (backward-compatible behaviour).
     * If one or more modes are present, the header is serialised as a comma-separated string and
     * servicelinker will restrict generation to those modes.
     * Note: the empty-set case (no service links at all) is handled upstream by shouldSkipServicelinker(),
     * so this method is only called when modes is null or non-empty.
     */
    public void setServiceLinkModesHeader(Exchange exchange) {
        String referential = datasetReferentialFor(exchange);
        if (referential.startsWith("rb_")) {
            referential = referential.replace("rb_", "");
        }
        Provider provider = getProvider(referential);
        Set<String> modes = provider.getChouetteInfo().getGenerateMissingServiceLinksForModes();
        if (modes != null) {
            exchange.getIn().setHeader(Constants.SERVICE_LINK_MODES_HEADER, String.join(",", modes));
        }
    }

    @SuppressWarnings("unused") // Used by ServicelinkerEnrichmentStatusRouteBuilder
    public String pathToNetexForServicelinker(Exchange exchange) {
        String referential = datasetReferentialFor(exchange);
        return "servicelinker/" + referential + "/" + correlationIdFor(exchange) + "/" + referential + "-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME;
    }

    public String pathToNetexExportFromChouetteToMergeWithFlex(Exchange exchange) {
        return BLOBSTORE_PATH_CHOUETTE + "netex/" + chouetteReferentialFor(exchange) + "-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME;
    }

    public String pathToNetexFromAshurToMergeWithFlex(Exchange exchange) {
        String referential = chouetteReferentialFor(exchange);
        return "filtered-netex/" + referential + "/netex-before-merging/" + correlationIdFor(exchange) + "/" + referential + "-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME;
    }

    @SuppressWarnings("unused") // Used by AntuNetexValidationStatusRouteBuilder and AshurFilteringStatusRouteBuilder
    public String pathToNetexWithoutBlocksProducedByAshur(Exchange exchange) {
        String referential = chouetteReferentialFor(exchange);
        return "filtered-netex/" + referential + "/netex-without-blocks/" + correlationIdFor(exchange) + "/" + referential + "-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME;
    }

    public String pathToExportedNetexFileToMergeWithFlex(Exchange exchange) {
        if (shouldRunExperimentalImport(exchange)) {
            return pathToNetexFromAshurToMergeWithFlex(exchange);
        }
        return pathToNetexExportFromChouetteToMergeWithFlex(exchange);
    }

    public String flexibleDataWorkingDirectory(Exchange exchange) {
        if (shouldRunExperimentalImport(exchange)) {
            return exchange.getProperty(FOLDER_NAME, String.class) + "/" + correlationIdFor(exchange) + UNPACKED_WITH_FLEXIBLE_LINES_SUB_FOLDER;
        }
        return exchange.getProperty(FOLDER_NAME, String.class) + UNPACKED_WITH_FLEXIBLE_LINES_SUB_FOLDER;
    }

    public String directoryForMergedNetex(Exchange exchange) {
        if (shouldRunExperimentalImport(exchange)) {
            return exchange.getProperty(FOLDER_NAME, String.class) + "/" + correlationIdFor(exchange) + MERGED_NETEX_SUB_FOLDER;
        }
        return exchange.getProperty(FOLDER_NAME, String.class) + MERGED_NETEX_SUB_FOLDER;
    }

    private String correlationIdFor(Exchange exchange) {
        return exchange.getIn().getHeader(Constants.CORRELATION_ID, String.class);
    }

    private String datasetReferentialFor(Exchange exchange) {
        return exchange.getIn().getHeader(Constants.DATASET_REFERENTIAL, String.class);
    }

    private String chouetteReferentialFor(Exchange exchange) {
        return exchange.getIn().getHeader(Constants.CHOUETTE_REFERENTIAL, String.class);
    }
}

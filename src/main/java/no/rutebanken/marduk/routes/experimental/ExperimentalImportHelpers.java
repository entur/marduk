package no.rutebanken.marduk.routes.experimental;

import no.rutebanken.marduk.Constants;
import org.apache.camel.Exchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.Constants.FOLDER_NAME;

@Component
public class ExperimentalImportHelpers {
    private final boolean experimentalImportEnabled;
    private final List<String> experimentalCodespaces;

    private static final String EXPORT_FILE_NAME = "netex/${header." + CHOUETTE_REFERENTIAL + "}-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME;
    private static final String UNPACKED_WITH_FLEXIBLE_LINES_SUB_FOLDER = "/unpacked-with-flexible-lines";
    private static final String MERGED_NETEX_SUB_FOLDER = "/result";

    public ExperimentalImportHelpers(
        @Value("${marduk.experimental-import.enabled:false}") boolean experimentalImportEnabled,
        @Value("${marduk.experimental-import.codespaces:}") List<String> experimentalCodespaces
    ) {
        this.experimentalImportEnabled = experimentalImportEnabled;
        this.experimentalCodespaces = experimentalCodespaces;
    }

    public boolean shouldRunExperimentalImport(Exchange exchange) {
        if (experimentalImportEnabled) {
            String referential = exchange.getIn().getHeader(Constants.DATASET_REFERENTIAL, String.class);
            if (referential != null) {
                return experimentalCodespaces.contains(referential);
            }
        }
        return false;
    }

    public String pathToNetexFileExportedFromChouette() {
        return BLOBSTORE_PATH_CHOUETTE + EXPORT_FILE_NAME;
    }

    public String pathToNetexFileProducedByAshur(Exchange exchange) {
        return BLOBSTORE_PATH_CHOUETTE + correlationIdFor(exchange) + "/netex/" + chouetteReferentialFor(exchange) + "-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME;
    }

    public String pathToExportedNetexFile(Exchange exchange) {
        if (shouldRunExperimentalImport(exchange)) {
            return pathToNetexFileProducedByAshur(exchange);
        }
        return pathToNetexFileExportedFromChouette();
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

    private String chouetteReferentialFor(Exchange exchange) {
        return exchange.getIn().getHeader(Constants.CHOUETTE_REFERENTIAL, String.class);
    }
}

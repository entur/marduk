package no.rutebanken.marduk.routes.experimental;

import no.rutebanken.marduk.Constants;
import org.apache.camel.Exchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExperimentalImportFilter {
    private final boolean experimentalImportEnabled;
    private final List<String> experimentalCodespaces;

    public ExperimentalImportFilter(
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
}

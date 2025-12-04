package no.rutebanken.marduk.routes.experimental;

import no.rutebanken.marduk.Constants;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class ExperimentalImportHelpersTest {

    private Exchange exchange() {
        CamelContext ctx = new DefaultCamelContext();
        return new DefaultExchange(ctx);
    }

    @Test
    void testShouldRunExperimentalImportIfEnabledForCurrentCodespace() {
        Exchange exchange = exchange();
        exchange.getIn().setHeader(Constants.DATASET_REFERENTIAL, "TST");
        ExperimentalImportHelpers filter = new ExperimentalImportHelpers(true, List.of("TST"));
        Assertions.assertTrue(filter.shouldRunExperimentalImport(exchange));
    }

    @Test
    void testShouldNotRunExperimentalImportIfNotEnabledForCurrentCodespace() {
        Exchange exchange = exchange();
        exchange.getIn().setHeader(Constants.DATASET_REFERENTIAL, "TST");
        ExperimentalImportHelpers filter = new ExperimentalImportHelpers(true, List.of("NOMATCH"));
        Assertions.assertFalse(filter.shouldRunExperimentalImport(exchange));
    }

    @Test
    void testShouldNotRunExperimentalImportIfEnabledForNoCodespaces() {
        ExperimentalImportHelpers filter = new ExperimentalImportHelpers(true, List.of());
        Assertions.assertFalse(filter.shouldRunExperimentalImport(exchange()));
    }

    @Test
    void testShouldNotRunExperimentalImportIfDisabled() {
        ExperimentalImportHelpers filter = new ExperimentalImportHelpers(false, List.of());
        Assertions.assertFalse(filter.shouldRunExperimentalImport(exchange()));
    }
}
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

    @Test
    void testPathToExportedNetexFileForExperimentalImport() {
        Exchange exchange = exchange();
        exchange.getIn().setHeader(Constants.DATASET_REFERENTIAL, "TST");
        exchange.getIn().setHeader(Constants.CHOUETTE_REFERENTIAL, "TST");
        exchange.getIn().setHeader(Constants.CORRELATION_ID, "correlation");
        ExperimentalImportHelpers helpers = new ExperimentalImportHelpers(true, List.of("TST"));
        String expectedPath = Constants.BLOBSTORE_PATH_CHOUETTE + "correlation/netex/TST-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME;
        Assertions.assertEquals(expectedPath, helpers.pathToExportedNetexFile(exchange));
    }

    @Test
    void testPathToExportedNetexFileForNormalImport() {
        Exchange exchange = exchange();
        exchange.getIn().setHeader(Constants.CHOUETTE_REFERENTIAL, "TST");
        ExperimentalImportHelpers helpers = new ExperimentalImportHelpers(false, List.of());
        String expectedPath = Constants.BLOBSTORE_PATH_CHOUETTE + "netex/TST-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME;
        Assertions.assertEquals(expectedPath, helpers.pathToExportedNetexFile(exchange));
    }

    @Test
    void testFlexibleDataWorkingDirectoryForExperimentalImport() {
        Exchange exchange = exchange();
        exchange.getIn().setHeader(Constants.DATASET_REFERENTIAL, "TST");
        exchange.getIn().setHeader(Constants.CORRELATION_ID, "correlation");
        exchange.setProperty(Constants.FOLDER_NAME, "/base/folder");
        ExperimentalImportHelpers helpers = new ExperimentalImportHelpers(true, List.of("TST"));
        String expectedPath = "/base/folder/correlation/unpacked-with-flexible-lines";
        Assertions.assertEquals(expectedPath, helpers.flexibleDataWorkingDirectory(exchange));
    }

    @Test
    void testFlexibleDataWorkingDirectoryForNormalImport() {
        Exchange exchange = exchange();
        exchange.setProperty(Constants.FOLDER_NAME, "/base/folder");
        exchange.getIn().setHeader(Constants.CORRELATION_ID, "correlation");
        ExperimentalImportHelpers helpers = new ExperimentalImportHelpers(false, List.of());
        String expectedPath = "/base/folder/unpacked-with-flexible-lines";
        Assertions.assertEquals(expectedPath, helpers.flexibleDataWorkingDirectory(exchange));
    }

    @Test
    void testDirectoryForMergedNetexForExperimentalImport() {
        Exchange exchange = exchange();
        exchange.getIn().setHeader(Constants.DATASET_REFERENTIAL, "TST");
        exchange.getIn().setHeader(Constants.CORRELATION_ID, "correlation");
        exchange.setProperty(Constants.FOLDER_NAME, "/base/folder");
        ExperimentalImportHelpers helpers = new ExperimentalImportHelpers(true, List.of("TST"));
        String expectedPath = "/base/folder/correlation/result";
        Assertions.assertEquals(expectedPath, helpers.directoryForMergedNetex(exchange));
    }

    @Test
    void testDirectoryForMergedNetexForNormalImport() {
        Exchange exchange = exchange();
        exchange.setProperty(Constants.FOLDER_NAME, "/base/folder");
        exchange.getIn().setHeader(Constants.CORRELATION_ID, "correlation");
        ExperimentalImportHelpers helpers = new ExperimentalImportHelpers(false, List.of());
        String expectedPath = "/base/folder/result";
        Assertions.assertEquals(expectedPath, helpers.directoryForMergedNetex(exchange));
    }
}
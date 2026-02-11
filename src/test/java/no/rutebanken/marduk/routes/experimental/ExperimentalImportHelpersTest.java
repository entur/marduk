package no.rutebanken.marduk.routes.experimental;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukSpringBootBaseTest;
import org.apache.camel.Exchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.when;

class ExperimentalImportHelpersTest extends MardukSpringBootBaseTest {
    @Test
    void testShouldRunExperimentalImportIfEnabledForCodespace() {
        when(providerRepository.getProviders()).thenReturn(List.of(providerWithExperimentalImport()));
        Exchange exchange = exchange();
        ExperimentalImportHelpers filter = new ExperimentalImportHelpers(true, providerRepository);
        Assertions.assertTrue(filter.shouldRunExperimentalImport(exchange));
    }

    @Test
    void testShouldNotRunExperimentalImportIfNotEnabledForCurrentCodespace() {
        when(providerRepository.getProviders()).thenReturn(List.of(providerWithoutExperimentalImport()));
        Exchange exchange = exchange();
        ExperimentalImportHelpers filter = new ExperimentalImportHelpers(true, providerRepository);
        Assertions.assertFalse(filter.shouldRunExperimentalImport(exchange));
    }

    @Test
    void testShouldNotRunExperimentalImportIfDisabledForAllCodespaces() {
        ExperimentalImportHelpers filter = new ExperimentalImportHelpers(false, providerRepository);
        when(providerRepository.getProviders()).thenReturn(List.of(providerWithExperimentalImport()));
        Assertions.assertFalse(filter.shouldRunExperimentalImport(exchange()));
        when(providerRepository.getProviders()).thenReturn(List.of(providerWithoutExperimentalImport()));
        Assertions.assertFalse(filter.shouldRunExperimentalImport(exchange()));
    }

    @Test
    void testPathToExportedNetexFileToMergeWithFlexForExperimentalImport() {
        when(providerRepository.getProviders()).thenReturn(List.of(providerWithExperimentalImport()));
        Exchange exchange = exchange();
        ExperimentalImportHelpers helpers = new ExperimentalImportHelpers(true, providerRepository);
        String expectedPath = "filtered-netex/TST/netex-before-merging/correlation/TST-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME;
        Assertions.assertEquals(expectedPath, helpers.pathToExportedNetexFileToMergeWithFlex(exchange));
    }

    @Test
    void testPathToExportedNetexFileToMergeWithFlexForNormalImport() {
        when(providerRepository.getProviders()).thenReturn(List.of(providerWithoutExperimentalImport()));
        Exchange exchange = exchange();
        ExperimentalImportHelpers helpers = new ExperimentalImportHelpers(false, providerRepository);
        String expectedPath = Constants.BLOBSTORE_PATH_CHOUETTE + "netex/TST-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME;
        Assertions.assertEquals(expectedPath, helpers.pathToExportedNetexFileToMergeWithFlex(exchange));
    }

    @Test
    void testFlexibleDataWorkingDirectoryForExperimentalImport() {
        when(providerRepository.getProviders()).thenReturn(List.of(providerWithExperimentalImport()));
        Exchange exchange = exchange();
        ExperimentalImportHelpers helpers = new ExperimentalImportHelpers(true, providerRepository);
        String expectedPath = "/base/folder/correlation/unpacked-with-flexible-lines";
        Assertions.assertEquals(expectedPath, helpers.flexibleDataWorkingDirectory(exchange));
    }

    @Test
    void testFlexibleDataWorkingDirectoryForNormalImport() {
        when(providerRepository.getProviders()).thenReturn(List.of(providerWithoutExperimentalImport()));
        Exchange exchange = exchange();
        ExperimentalImportHelpers helpers = new ExperimentalImportHelpers(false, providerRepository);
        String expectedPath = "/base/folder/unpacked-with-flexible-lines";
        Assertions.assertEquals(expectedPath, helpers.flexibleDataWorkingDirectory(exchange));
    }

    @Test
    void testDirectoryForMergedNetexForExperimentalImport() {
        when(providerRepository.getProviders()).thenReturn(List.of(providerWithExperimentalImport()));
        Exchange exchange = exchange();
        ExperimentalImportHelpers helpers = new ExperimentalImportHelpers(true, providerRepository);
        String expectedPath = "/base/folder/correlation/result";
        Assertions.assertEquals(expectedPath, helpers.directoryForMergedNetex(exchange));
    }

    @Test
    void testDirectoryForMergedNetexForNormalImport() {
        when(providerRepository.getProviders()).thenReturn(List.of(providerWithoutExperimentalImport()));
        Exchange exchange = exchange();
        ExperimentalImportHelpers helpers = new ExperimentalImportHelpers(false, providerRepository);
        String expectedPath = "/base/folder/result";
        Assertions.assertEquals(expectedPath, helpers.directoryForMergedNetex(exchange));
    }

    @Test
    void testShouldRunExperimentalImportWithRbPrefixedReferential() {
        when(providerRepository.getProviders()).thenReturn(List.of(providerWithExperimentalImport()));
        Exchange exchange = exchange();
        exchange.getIn().setHeader(Constants.DATASET_REFERENTIAL, "rb_TST");
        ExperimentalImportHelpers filter = new ExperimentalImportHelpers(true, providerRepository);
        Assertions.assertTrue(filter.shouldRunExperimentalImport(exchange));
    }
}
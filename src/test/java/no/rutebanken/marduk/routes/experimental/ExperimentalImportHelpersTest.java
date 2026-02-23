package no.rutebanken.marduk.routes.experimental;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukSpringBootBaseTest;
import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import org.apache.camel.Exchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

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

    // --- setServiceLinkModesHeader tests ---

    @Test
    void testSetServiceLinkModesHeaderNotSetWhenModesIsNull() {
        // providerWithExperimentalImport() leaves generateMissingServiceLinksForModes null
        when(providerRepository.getProviders()).thenReturn(List.of(providerWithExperimentalImport()));
        Exchange exchange = exchange();
        ExperimentalImportHelpers helpers = new ExperimentalImportHelpers(true, providerRepository);

        helpers.setServiceLinkModesHeader(exchange);

        Assertions.assertNull(
            exchange.getIn().getHeader(Constants.SERVICE_LINK_MODES_HEADER),
            "Header should not be set when generateMissingServiceLinksForModes is null"
        );
    }

    @Test
    void testSetServiceLinkModesHeaderIsEmptyStringWhenModesIsEmptySet() {
        when(providerRepository.getProviders()).thenReturn(List.of(providerWithServiceLinkModes(Set.of())));
        Exchange exchange = exchange();
        ExperimentalImportHelpers helpers = new ExperimentalImportHelpers(true, providerRepository);

        helpers.setServiceLinkModesHeader(exchange);

        Assertions.assertEquals(
            "",
            exchange.getIn().getHeader(Constants.SERVICE_LINK_MODES_HEADER, String.class),
            "Empty mode set should produce an empty header value (signals: generate for no modes)"
        );
    }

    @Test
    void testSetServiceLinkModesHeaderContainsAllConfiguredModes() {
        when(providerRepository.getProviders()).thenReturn(List.of(providerWithServiceLinkModes(Set.of("BUS", "RAIL"))));
        Exchange exchange = exchange();
        ExperimentalImportHelpers helpers = new ExperimentalImportHelpers(true, providerRepository);

        helpers.setServiceLinkModesHeader(exchange);

        String header = exchange.getIn().getHeader(Constants.SERVICE_LINK_MODES_HEADER, String.class);
        Assertions.assertNotNull(header, "Header should be set when modes are configured");
        Assertions.assertEquals(Set.of("BUS", "RAIL"), Set.of(header.split(",")),
            "Header should contain exactly the configured modes (order-independent)");
    }

    @Test
    void testSetServiceLinkModesHeaderStripsRbPrefix() {
        when(providerRepository.getProviders()).thenReturn(List.of(providerWithServiceLinkModes(Set.of("FERRY"))));
        Exchange exchange = exchange();
        exchange.getIn().setHeader(Constants.DATASET_REFERENTIAL, "rb_TST");
        ExperimentalImportHelpers helpers = new ExperimentalImportHelpers(true, providerRepository);

        helpers.setServiceLinkModesHeader(exchange);

        Assertions.assertEquals(
            "FERRY",
            exchange.getIn().getHeader(Constants.SERVICE_LINK_MODES_HEADER, String.class),
            "rb_ prefix should be stripped when looking up the provider"
        );
    }

    private Provider providerWithServiceLinkModes(Set<String> modes) {
        Provider provider = new Provider();
        provider.setId(testProviderId);
        ChouetteInfo chouetteInfo = new ChouetteInfo();
        chouetteInfo.setEnableExperimentalImport(true);
        chouetteInfo.setReferential(testDatasetReferential);
        chouetteInfo.setGenerateMissingServiceLinksForModes(modes);
        provider.setChouetteInfo(chouetteInfo);
        return provider;
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
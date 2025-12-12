package no.rutebanken.marduk.routes.experimental;

import no.rutebanken.marduk.MardukSpringBootBaseTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static no.rutebanken.marduk.Constants.*;
import static org.mockito.Mockito.when;

class SetProviderIdBeforeFlexMergeProcessorTest extends MardukSpringBootBaseTest {
    SetProviderIdBeforeFlexMergeProcessor initializeProcessor(Boolean experimentalImportEnabled) {
        ExperimentalImportHelpers experimentalImportHelpers = new ExperimentalImportHelpers(
            experimentalImportEnabled,
            providerRepository
        );
        return new SetProviderIdBeforeFlexMergeProcessor(
            experimentalImportHelpers,
            providerRepository
        );
    }

    @Test
    void testProcessorDoesNotChangeProviderIdForExperimentalImports() throws Exception {
        when(providerRepository.getProviderId(testDatasetReferential)).thenReturn(testProviderId);
        when(providerRepository.getProvider(testProviderId)).thenReturn(providerWithExperimentalImport());

        var exchange = exchange();
        SetProviderIdBeforeFlexMergeProcessor processor = initializeProcessor(true);
        processor.process(exchange);

        Long actualProviderId = exchange.getIn().getHeader(PROVIDER_ID, Long.class);
        Assertions.assertEquals(testProviderId, actualProviderId);
    }

    @Test
    void testProcessorUpdatesProviderIdForChouetteImports() throws Exception {
        when(providerRepository.getProviderId(testDatasetReferential)).thenReturn(testRbProviderId);

        var exchange = exchange();
        SetProviderIdBeforeFlexMergeProcessor processor = initializeProcessor(false);
        processor.process(exchange);

        Long actualProviderId = exchange.getIn().getHeader(PROVIDER_ID, Long.class);
        Assertions.assertEquals(testRbProviderId, actualProviderId);
    }
}
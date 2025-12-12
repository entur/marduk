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
        when(providerRepository.getProviderId(TEST_DATASET_REFERENTIAL)).thenReturn(TEST_PROVIDER_ID);
        when(providerRepository.getProvider(TEST_PROVIDER_ID)).thenReturn(providerWithExperimentalImport());

        var exchange = exchange();
        SetProviderIdBeforeFlexMergeProcessor processor = initializeProcessor(true);
        processor.process(exchange);

        Long actualProviderId = exchange.getIn().getHeader(PROVIDER_ID, Long.class);
        Assertions.assertEquals(TEST_PROVIDER_ID, actualProviderId);
    }

    @Test
    void testProcessorUpdatesProviderIdForChouetteImports() throws Exception {
        when(providerRepository.getProviderId(TEST_DATASET_REFERENTIAL)).thenReturn(TEST_RB_PROVIDER_ID);

        var exchange = exchange();
        SetProviderIdBeforeFlexMergeProcessor processor = initializeProcessor(false);
        processor.process(exchange);

        Long actualProviderId = exchange.getIn().getHeader(PROVIDER_ID, Long.class);
        Assertions.assertEquals(TEST_RB_PROVIDER_ID, actualProviderId);
    }
}
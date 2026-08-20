package no.rutebanken.marduk.routes.experimental;

import no.rutebanken.marduk.MardukSpringBootBaseTest;
import no.rutebanken.marduk.pipeline.MardukMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static no.rutebanken.marduk.Constants.*;
import static org.mockito.Mockito.when;

class SetProviderIdBeforeFlexMergeProcessorTest extends MardukSpringBootBaseTest {

    SetProviderIdBeforeFlexMergeProcessor initializeProcessor(Boolean experimentalImportEnabled) {
        return new SetProviderIdBeforeFlexMergeProcessor(
            new ExperimentalImportHelpers(experimentalImportEnabled, providerRepository),
            providerRepository
        );
    }

    @Test
    void testProcessorDoesNotChangeProviderIdForExperimentalImports() {
        // useExperimentalImport is configured on the non-rb_ provider, so switching to the rb_ provider here
        // would send the rest of the merge down the wrong path.
        when(providerRepository.getProviderId(testDatasetReferential)).thenReturn(testProviderId);
        when(providerRepository.getProviders()).thenReturn(List.of(providerWithExperimentalImport()));

        MardukMessage message = message();
        initializeProcessor(true).setProviderIdIfChouetteImport(message);

        Assertions.assertEquals(testProviderId, message.getHeader(PROVIDER_ID, Long.class));
    }

    @Test
    void testProcessorUpdatesProviderIdForChouetteImports() {
        when(providerRepository.getProviderId(testDatasetReferential)).thenReturn(testRbProviderId);

        MardukMessage message = message();
        initializeProcessor(false).setProviderIdIfChouetteImport(message);

        Assertions.assertEquals(testRbProviderId, message.getHeader(PROVIDER_ID, Long.class));
    }
}

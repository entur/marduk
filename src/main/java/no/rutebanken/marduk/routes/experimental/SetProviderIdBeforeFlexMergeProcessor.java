package no.rutebanken.marduk.routes.experimental;

import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.repository.ProviderRepository;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

/***
 * When running a Chouette import (i.e., not an experimental import), this sets the correct provider ID
 * based on the Chouette referential name. This ensures that subsequent processing steps use the appropriate provider context.
 *
 * Note: For experimental imports, it is essential that the provider ID is not altered before merging flex. This is because
 * useExperimentalImport is defined on non rb_ providers, and changing it would result in incorrect behavior.
 */
@Component
public class SetProviderIdBeforeFlexMergeProcessor {

    private final ExperimentalImportHelpers experimentalImportHelpers;
    private final ProviderRepository providerRepository;

    public SetProviderIdBeforeFlexMergeProcessor(
        ExperimentalImportHelpers experimentalImportHelpers,
        ProviderRepository providerRepository
    ) {
        this.experimentalImportHelpers = experimentalImportHelpers;
        this.providerRepository = providerRepository;
    }

    public void setProviderIdIfChouetteImport(MardukMessage message) {
        if (!this.experimentalImportHelpers.shouldRunExperimentalImport(message)) {
            String chouetteReferential = message.getHeader(CHOUETTE_REFERENTIAL, String.class);
            message.setHeader(PROVIDER_ID, providerRepository.getProviderId(chouetteReferential));
        }
    }
}

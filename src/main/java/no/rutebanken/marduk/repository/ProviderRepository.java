package no.rutebanken.marduk.repository;

import no.rutebanken.marduk.domain.Provider;

import java.util.Collection;

public interface ProviderRepository {

    Collection<Provider> getProviders();

    Provider getProvider(Long id);

    boolean isConnected();
}
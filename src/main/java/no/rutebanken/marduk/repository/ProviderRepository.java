package no.rutebanken.marduk.repository;

import no.rutebanken.marduk.domain.Provider;

import java.util.Collection;

public interface ProviderRepository {

    Collection<Provider> getProvidersWithSftpAccounts();

    Provider getProviderById(Long id);
}

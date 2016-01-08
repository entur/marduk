package no.rutebanken.marduk.management;

import java.util.List;

public interface ProviderRepository {

    List<Provider> getProvidersWithSftpAccounts();

    Provider getProviderById(Long id);
}

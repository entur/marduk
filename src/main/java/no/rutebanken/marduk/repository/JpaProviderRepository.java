package no.rutebanken.marduk.repository;

import no.rutebanken.marduk.domain.Provider;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Collection;

@Repository
@Transactional
public class JpaProviderRepository implements ProviderRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Collection<Provider> getProvidersWithSftpAccounts() {
        return this.entityManager.createQuery("SELECT p FROM Provider p WHERE p.sftpAccount IS NOT NULL", Provider.class)
                .getResultList();
    }

    @Override
    public Provider getProviderById(Long id) {
        return entityManager.find(Provider.class, id);
    }

}

package no.rutebanken.marduk.repository;

import no.rutebanken.marduk.App;
import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import org.assertj.core.api.Condition;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(App.class)
public class JpaProviderRepositoryTest {

    @Autowired
    ProviderRepository repository;

    @Test
    public void testGetProvidersWithSftpAccounts() {
        Collection<Provider> providers = repository.getProvidersWithSftpAccounts();
        assertThat(providers).hasSize(1);
        assertThat(providers).doNotHave( new Condition<Provider>() {
            @Override
            public boolean matches(Provider provider) {
                return provider.sftpAccount == null;
            }
        });
    }

    @Test
    public void testGetProviderById() {
        Provider provider = repository.getProviderById(42L);
        assertThat(provider).isEqualTo(new Provider(42L, "Flybussekspressen", "42",
                new ChouetteInfo(1L, "flybussekspressen", "flybussekspressen", "Rutebanken", "admin@rutebanken.org")));
    }

}
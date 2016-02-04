package no.rutebanken.marduk.management;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class ProviderRepositoryImpl implements ProviderRepository {

    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    JdbcTemplate jdbcTemplate;

    @PostConstruct  //TODO setup dedicated database
    public void init() {
        jdbcTemplate.execute("DROP TABLE providers IF EXISTS");
        jdbcTemplate.execute("CREATE TABLE providers(id SERIAL, name VARCHAR(255), sftp_account VARCHAR(255), chouette_prefix VARCHAR(255), chouette_data_space VARCHAR(255), chouette_organisation VARCHAR(255), chouette_user VARCHAR(255) )");

        populate();
    }

    public void populate() {
        List<Provider> providers = new ArrayList<>();
        providers.add(new Provider(null, "Rutebanken", "nvdb", new ChouetteInfo("tds1", "testDS1", "Rutebanken1", "tg@scienta.no")));
        providers.add(new Provider(null, "Rutebanken", "kartverk", new ChouetteInfo("tds1", "tds1", "Rutebanken", "admin@rutebanken.org")));

        List<Object[]> splitUpProviders = providers.stream()
                .map(provider -> new Object[]{provider.getName(), provider.getSftpAccount(),
                        provider.getChouetteInfo().getPrefix(), provider.getChouetteInfo().getDataSpace(), provider.getChouetteInfo().getOrganisation(), provider.getChouetteInfo().getUser()} )
                .collect(Collectors.toList());

        jdbcTemplate.batchUpdate("INSERT INTO providers(name, sftp_account, chouette_prefix, chouette_data_space, chouette_organisation, chouette_user) VALUES (?,?,?,?,?,?)", splitUpProviders);
    }

    @Override
    public List<Provider> getProvidersWithSftpAccounts() {
        log.info("Querying for provider records where sftp account is non-empty.");
        return query("SELECT * FROM providers WHERE sftp_account IS NOT NULL");
    }

    @Override
    public Provider getProviderById(Long id) {
        log.info("Querying for provider records with id '" + id);
        return query("SELECT * FROM providers WHERE id = " + id).get(0);
    }

    public List<Provider> query(String queryString) {
        log.info("Running query '" + queryString + "'");
        List<Provider> providers = jdbcTemplate.query(queryString, new Object[]{},
                (rs, rowNum) -> new Provider(rs.getLong("id"), rs.getString("name"), rs.getString("sftp_account"),
                        new ChouetteInfo(rs.getString("chouette_prefix"), rs.getString("chouette_data_space"), rs.getString("chouette_organisation"), rs.getString("chouette_user") ))
        );
        providers.forEach(provider -> log.info(provider.toString()));
        return providers;
    }

}

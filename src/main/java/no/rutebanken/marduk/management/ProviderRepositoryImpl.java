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

    @PostConstruct
    public void init() {
        jdbcTemplate.execute("DROP TABLE providers IF EXISTS");
        jdbcTemplate.execute("CREATE TABLE providers(id SERIAL, name VARCHAR(255), sftp_directory VARCHAR(255))");

        populate();
    }

    public void populate() {
        List<Provider> providers = new ArrayList<>();
        providers.add(new Provider(null, "Ruter", "kartverk", "rds", "rds", "Ruter"));
        providers.add(new Provider(null, "Rutebanken", "nvdb", "tds1", "testDS1", "Rutebanken1"));

        List<Object[]> splitUpProviders = providers.stream()
                .map(provider -> new Object[]{provider.getName(), provider.getSftpDirectory()} )
                .collect(Collectors.toList());

        jdbcTemplate.batchUpdate("INSERT INTO providers(name, sftp_directory) VALUES (?,?)", splitUpProviders);
    }

    public List<Provider> getProvidersWithSftpDirs() {
        log.info("Querying for provider records where sftp directory is non-empty.");
        List<Provider> providers = jdbcTemplate.query(
                "SELECT id, name, sftp_directory FROM providers WHERE sftp_directory IS NOT NULL", new Object[]{},
                (rs, rowNum) -> new Provider(rs.getLong("id"), rs.getString("name"), rs.getString("sftp_directory"), null, null, null)
        );
        providers.forEach(provider -> log.info(provider.toString()));
        return providers;
    }

}

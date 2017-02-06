package no.rutebanken.marduk.config;

import no.rutebanken.marduk.repository.FileNameAndDigestIdempotentRepository;
import org.apache.camel.spi.IdempotentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

public class IdempotentRepositoryConfig {

	@Autowired
	private DataSource datasource;

	@Bean
	public IdempotentRepository fileNameAndDigestIdempotentRepository() {
		return new FileNameAndDigestIdempotentRepository(datasource, "nameAndDigest");
	}


}

package no.rutebanken.marduk.config;

import no.rutebanken.marduk.repository.FileNameAndDigestIdempotentRepository;
import no.rutebanken.marduk.repository.UniqueDigestPerFileNameIdempotentRepository;
import org.apache.camel.processor.idempotent.jdbc.JdbcMessageIdRepository;
import org.apache.camel.spi.IdempotentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

public class IdempotentRepositoryConfig {

	@Autowired
	private DataSource datasource;

	@Bean
	public IdempotentRepository fileNameAndDigestIdempotentRepository() {
		return new FileNameAndDigestIdempotentRepository(datasource, "nameAndDigest");
	}

	@Bean
	public IdempotentRepository uniqueDigestPerFileNameIdempotentRepository() {
		return new UniqueDigestPerFileNameIdempotentRepository(datasource, "uniqueDigestForName");
	}

}

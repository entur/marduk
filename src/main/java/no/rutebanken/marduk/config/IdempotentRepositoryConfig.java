package no.rutebanken.marduk.config;

import org.apache.camel.processor.idempotent.jdbc.JdbcMessageIdRepository;
import org.apache.camel.spi.IdempotentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

public class IdempotentRepositoryConfig {

	@Autowired
	private DataSource datasource;

	@Autowired
	private TransactionTemplate transactionTemplate;


	@Bean
	public IdempotentRepository digestIdempotentRepository() {
		return new JdbcMessageIdRepository(datasource, transactionTemplate, "digest");	}


	@Bean
	public IdempotentRepository fileNameIdempotentRepository() {
		return new JdbcMessageIdRepository(datasource, transactionTemplate, "fileName");
	}

}

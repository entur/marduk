package no.rutebanken.marduk.services;

import org.apache.camel.spi.IdempotentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IdempotentRepositoryService {

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	@Autowired
	IdempotentRepository fileNameAndDigestIdempotentRepository;

	public void clean() {
		logger.info("Starting cleaning of idempotent message repository.");
		fileNameAndDigestIdempotentRepository.clear();
		logger.info("Done cleaning idempotent message repository.");
	}
}

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

	@Autowired
	IdempotentRepository idempotentDownloadRepository;

	public void cleanUniqueFileNameAndDigestRepo() {
		logger.info("Starting cleaning of unique file name and digest idempotent message repository.");
		fileNameAndDigestIdempotentRepository.clear();
		logger.info("Done cleaning unique file name and digest idempotent message repository.");
	}

	public void cleanIdempotentDownloadRepo() {
		logger.info("Starting cleaning of idempotent download repository.");
		idempotentDownloadRepository.clear();
		logger.info("Done cleaning idempotent download repository.");
	}
}

package no.rutebanken.marduk.services;

import org.apache.camel.processor.idempotent.FileIdempotentRepository;
import org.apache.camel.spi.IdempotentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class IdempotentFileStoreService {

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	@Autowired
	IdempotentRepository digestIdempotentRepository;

	@Autowired
	IdempotentRepository fileNameIdempotentRepository;

	public void clean() {
		logger.info("Starting cleaning of filestores.");
		fileNameIdempotentRepository.clear();
		digestIdempotentRepository.clear();
		logger.info("Done cleaning filestores.");
	}
}

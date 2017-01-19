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
        try {
            logger.info("Starting cleaning of filestores.");
            fileNameIdempotentRepository.clear();
            ((FileIdempotentRepository) fileNameIdempotentRepository).reset();
            digestIdempotentRepository.clear();
            ((FileIdempotentRepository) digestIdempotentRepository).reset();
            logger.info("Done cleaning filestores.");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

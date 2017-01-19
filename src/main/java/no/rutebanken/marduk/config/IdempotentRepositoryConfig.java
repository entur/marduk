package no.rutebanken.marduk.config;

import org.apache.camel.processor.idempotent.FileIdempotentRepository;
import org.apache.camel.spi.IdempotentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import java.io.File;

public class IdempotentRepositoryConfig {

    @Value("${idempotent.file.store.path}")
    private String idempotentFileStorePath;

    @Value("${idempotent.file.store.cache.size:5000000}")
    private int cacheSize;

    @Value("${idempotent.file.store.max.size:536870912}")  //0,5 GB pr store
    private long maxFileStoreSize;

    private final String digestFilterFile = "digeststore.dat";

    private final String fileNameFilterFile = "filenamestore.dat";

    @Bean
    public IdempotentRepository digestIdempotentRepository() {
    	
    	File folder = new File(idempotentFileStorePath);
    	folder.mkdirs();
    	
        FileIdempotentRepository idempotentRepository = new FileIdempotentRepository();
        idempotentRepository.setFileStore(new File(folder,digestFilterFile));
        idempotentRepository.setMaxFileStoreSize(maxFileStoreSize);
        idempotentRepository.setCacheSize(cacheSize);
        return idempotentRepository;
    }

    @Bean
    public IdempotentRepository fileNameIdempotentRepository() {
    	File folder = new File(idempotentFileStorePath);
    	folder.mkdirs();

    	FileIdempotentRepository idempotentRepository = new FileIdempotentRepository();
        idempotentRepository.setFileStore(new File(folder,fileNameFilterFile));
        idempotentRepository.setMaxFileStoreSize(maxFileStoreSize);
        idempotentRepository.setCacheSize(cacheSize);
        return idempotentRepository;
    }

}

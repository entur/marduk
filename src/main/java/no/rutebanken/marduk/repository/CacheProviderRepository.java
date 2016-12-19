package no.rutebanken.marduk.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import no.rutebanken.marduk.domain.Provider;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;


@Repository
public class CacheProviderRepository implements ProviderRepository {

    @Autowired
    RestProviderDAO restProviderService;

    @Value("${provider.cache.file.path}")
    private String cacheFilePath;

    @Value("${marduk.provider.cache.refresh.max.size:100}")
    private Integer cacheMaxSize;

    private static Cache<Long, Provider> cache;

    private static final String FILENAME = "providerCache.json";

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @PostConstruct
    void init() {
        cache = CacheBuilder.newBuilder().maximumSize(cacheMaxSize).build();
    }

    @Scheduled(fixedRateString = "${marduk.provider.cache.refresh.interval:300000}")
    public void populate() {
        if (restProviderService.isConnected()){
            Collection<Provider> newProviders = restProviderService.getProviders();
            Map<Long, Provider> providerMap = newProviders.stream().collect(Collectors.toMap(p -> p.getId(), p -> p));
            if (providerMap.isEmpty()){
                logger.warn("Result from REST Provider Service is empty. Skipping provider cache update. Keeping " + cache.size() + " existing elements.");
                return;
            }
            cache.putAll(providerMap);
            logger.info("Updated provider cache with result from REST Provider Service. Cache now has " + cache.size() + " elements");
            writeCacheToFile();
        } else {
            if (isEmpty()){
                logger.warn("REST Provider Service is unavailable and provider cache is empty. Trying to populate from file.");
                populateCacheFromFile(cacheFilePath);
            } else {
                logger.warn("REST Provider Service is unavailable. Could not update provider cache, but keeping " + cache.size() + " existing elements.");
            }
        }
    }

    private boolean isEmpty() {
        return cache.size() == 0;
    }

    public boolean isReady(){
        return !isEmpty();
    }

    private void populateCacheFromFile(String cacheFilePath) {
        File cacheFile = getCacheFile();
        if (cacheFile != null && cacheFile.exists()){
            try {
                logger.info("Populating provider cache from file '" + cacheFile + "'");
                ObjectMapper objectMapper = new ObjectMapper();
                Map<Long, Provider> map = objectMapper.readValue(new FileInputStream(cacheFile), new TypeReference<Map<Long, Provider>>(){});
                cache.putAll(map);
                logger.info("Provider cache now has " + cache.size() + " elements");
            } catch (IOException e){
                logger.error("Could not populate provider cache from file '" + cacheFilePath + "'.", e);
            }
        } else {
            logger.error("No cache file '" + cacheFile + "'. Cannot populate provider cache.");
        }
    }

    private void writeCacheToFile() {
        try {
            Map<Long, Provider> map = cache.asMap();
            File cacheFile = getCacheFile();
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.writeValue(cacheFile, map);
            logger.info("Updated provider cache file.");
        } catch (IOException e) {
            logger.error("Could not write provider cache to file '" + cacheFilePath + "'.", e);
        }
    }

    File getCacheFile(){
        try {
            File directory = new File(cacheFilePath);
            if (!directory.exists()) {
                FileUtils.forceMkdir(directory);
            }
            return new File(cacheFilePath + "/" + FILENAME);
        } catch (IOException e) {
            logger.error("Could not read provider cache file '" + cacheFilePath + "'.", e);
        }
        return null;
    }

    @Override
    public Collection<Provider> getProviders() {
        return cache.asMap().values();
    }

    @Override
    public Provider getProvider(Long id) {
        return cache.getIfPresent(id);
    }

    @Override
    public String getReferential(Long id) {
        return getProvider(id).chouetteInfo.referential;
    }

}

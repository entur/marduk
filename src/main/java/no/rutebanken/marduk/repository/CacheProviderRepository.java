/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.repository;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import no.rutebanken.marduk.domain.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.ResourceAccessException;

import javax.annotation.PostConstruct;
import java.net.ConnectException;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;


@Repository
public class CacheProviderRepository implements ProviderRepository {

    @Autowired
    RestProviderDAO restProviderService;

    @Value("${marduk.provider.cache.refresh.max.size:100}")
    private Integer cacheMaxSize;

    private static Cache<Long, Provider> cache;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @PostConstruct
    void init() {
        cache = CacheBuilder.newBuilder().maximumSize(cacheMaxSize).build();
    }

    @Scheduled(fixedRateString = "${marduk.provider.cache.refresh.interval:300000}")
    public void populate() {
        try {
            Collection<Provider> newProviders = restProviderService.getProviders();
            Map<Long, Provider> providerMap = newProviders.stream().collect(Collectors.toMap(p -> p.getId(), p -> p));
            if (providerMap.isEmpty()) {
                logger.warn("Result from REST Provider Service is empty. Skipping provider cache update. Keeping " + cache.size() + " existing elements.");
                return;
            }
            cache.putAll(providerMap);
            logger.info("Updated provider cache with result from REST Provider Service. Cache now has " + cache.size() + " elements");
        } catch (ResourceAccessException re) {
            if (re.getCause() instanceof ConnectException) {

                if (isEmpty()) {
                    logger.warn("REST Provider Service is unavailable and provider cache is empty. Trying to populate from file.");
                    throw re;
                } else {
                    logger.warn("REST Provider Service is unavailable. Could not update provider cache, but keeping " + cache.size() + " existing elements.");
                }
            } else {
                throw re;
            }
        }
    }

    private boolean isEmpty() {
        return cache.size() == 0;
    }

    public boolean isReady() {
        return !isEmpty();
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

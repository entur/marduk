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

package no.rutebanken.marduk.config;

import no.rutebanken.marduk.repository.InMemoryMardukBlobStoreRepository;
import no.rutebanken.marduk.repository.MardukBlobStoreRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@Profile("in-memory-blobstore")
public class InMemoryBlobStoreRepositoryConfig {

    @Bean
    public Map<String, Map<String, byte[]>> blobsInContainers() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    @Scope("prototype")
    MardukBlobStoreRepository blobStoreRepository(Map<String, Map<String, byte[]>> blobsInContainers) {
        return new InMemoryMardukBlobStoreRepository(blobsInContainers);
    }

}

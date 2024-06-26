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

import no.rutebanken.marduk.repository.GcsMardukBlobStoreRepository;
import no.rutebanken.marduk.repository.MardukBlobStoreRepository;
import no.rutebanken.marduk.repository.ProviderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;

@Configuration
@Profile("gcs-blobstore")
public class GcsBlobStoreRepositoryConfig {

    @Bean
    @Scope("prototype")
    MardukBlobStoreRepository blobStoreRepository(
            @Value("${blobstore.gcs.project.id}") String projectId,
            @Value("${blobstore.gcs.credential.path:#{null}}") String credentialPath,
            ProviderRepository providerRepository) {
        return new GcsMardukBlobStoreRepository(projectId, credentialPath, providerRepository);
    }

}

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

package no.rutebanken.marduk;

import no.rutebanken.marduk.config.GcsBlobStoreRepositoryConfig;
import no.rutebanken.marduk.config.IdempotentRepositoryConfig;
import no.rutebanken.marduk.repository.CacheProviderRepository;
import org.entur.pubsub.base.config.GooglePubSubConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;


/**
 * The application entry point.
 */
@SpringBootApplication(exclude={UserDetailsServiceAutoConfiguration.class})
@EnableScheduling
@Import({GcsBlobStoreRepositoryConfig.class, IdempotentRepositoryConfig.class, GooglePubSubConfig.class})
public class App {

	@Value("${marduk.provider.service.retry.interval:5000}")
	private Integer providerRetryInterval;

    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);

    @Autowired
	private CacheProviderRepository providerRepository;

    // must have a main method spring-boot can run
    public static void main(String... args) {
        LOGGER.info("Starting Marduk...");

	    SpringApplication.run(App.class,args);
    }

	/**
	 * Nothing starts consuming before the provider repository answers.
	 *
	 * <p>Every consumer needs it to turn a provider id into a referential, so a pod that starts without it
	 * would fail every message it touched.
	 */
	@jakarta.annotation.PostConstruct
	void awaitProviderRepository() throws InterruptedException {
		waitForProviderRepository();
	}

	protected void waitForProviderRepository() throws InterruptedException {
		while (true){
			try {
				providerRepository.populate();
				LOGGER.info("Provider Repository available.");
				return;
			} catch (Exception e) {
				LOGGER.warn("Provider Repository not available. Waiting {} secs before retrying...", providerRetryInterval/1000, e);
				Thread.sleep(providerRetryInterval);
			}
        }
	}

}

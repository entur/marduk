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

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import no.rutebanken.marduk.config.GcsStorageConfig;
import no.rutebanken.marduk.config.IdempotentRepositoryConfig;
import no.rutebanken.marduk.config.TransactionManagerConfig;
import no.rutebanken.marduk.repository.CacheProviderRepository;
import org.apache.camel.builder.RouteBuilder;
import org.entur.pubsub.config.GooglePubSubCamelComponentConfig;
import org.entur.pubsub.config.GooglePubSubConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.EnumSet;
import java.util.Set;

/**
 * A spring-boot application that includes a Camel route builder to setup the Camel routes
 */
@SpringBootApplication
@EnableScheduling
@Import({GcsStorageConfig.class, TransactionManagerConfig.class, IdempotentRepositoryConfig.class, GooglePubSubConfig.class})
public class App extends RouteBuilder {

	@Value("${marduk.shutdown.timeout:300}")
	private Long shutdownTimeout;

	@Value("${marduk.provider.service.retry.interval:5000}")
	private Integer providerRetryInterval;

    private static Logger logger = LoggerFactory.getLogger(App.class);

    @Autowired
	CacheProviderRepository providerRepository;

    // must have a main method spring-boot can run
    public static void main(String... args) {
        logger.info("Starting Marduk...");
        
        configureJsonPath();

	    SpringApplication.run(App.class,args);
    }

	@Override
	public void configure() throws Exception {
		waitForProviderRepository();

		getContext().getShutdownStrategy().setTimeout(shutdownTimeout);
		getContext().setUseMDCLogging(true);
	}

	protected void waitForProviderRepository() throws InterruptedException {
		while (!providerRepository.isReady()){
			logger.warn("Provider Repository not available. Waiting " + providerRetryInterval/1000 + " secs before retrying...");
			Thread.sleep(providerRetryInterval);
            providerRepository.populate();
        }
		logger.info("Provider Repository available. Starting camel routes...");
	}

	private static void configureJsonPath() {
		Configuration.setDefaults(new Configuration.Defaults() {

		    private final JsonProvider jsonProvider = new JacksonJsonProvider();
		    private final MappingProvider mappingProvider = new JacksonMappingProvider();

		    @Override
		    public JsonProvider jsonProvider() {
		        return jsonProvider;
		    }

		    @Override
		    public MappingProvider mappingProvider() {
		        return mappingProvider;
		    }

		    @Override
		    public Set<Option> options() {
		        return EnumSet.noneOf(Option.class);
		    }
		});	}

}

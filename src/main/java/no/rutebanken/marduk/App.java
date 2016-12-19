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
import org.apache.camel.spring.boot.FatJarRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
@Import({GcsStorageConfig.class, TransactionManagerConfig.class, IdempotentRepositoryConfig.class})
public class App extends FatJarRouter {

	@Value("${marduk.shutdown.timeout:300}")
	private Long shutdownTimeout;

	@Value("${marduk.provider.service.retry.interval:30000}")
	private Integer providerRetryInterval;

    private static Logger logger = LoggerFactory.getLogger(App.class);

    @Autowired
	CacheProviderRepository providerRepository;

    // must have a main method spring-boot can run
    public static void main(String... args) {
        logger.info("Starting Marduk...");
        
        configureJsonPath();

        FatJarRouter.main(args);
    }

	@Override
	public void configure() throws Exception {
    	super.configure();

		waitForProviderRepository();

		getContext().getShutdownStrategy().setTimeout(shutdownTimeout);
		getContext().setUseMDCLogging(true);
	}

	protected void waitForProviderRepository() throws InterruptedException {
		while (!providerRepository.isReady()){
			logger.warn("Not staring camel routes since provider repository not available. Waiting " + providerRetryInterval/1000 + " secs before retrying...");
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

package no.rutebanken.marduk;

import java.util.EnumSet;
import java.util.Set;

import no.rutebanken.marduk.config.GcsStorageConfig;
import no.rutebanken.marduk.config.TransactionManagerConfig;
import org.apache.camel.spring.boot.FatJarRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;

/**
 * A spring-boot application that includes a Camel route builder to setup the Camel routes
 */
@SpringBootApplication
@Import({GcsStorageConfig.class, TransactionManagerConfig.class})
public class App extends FatJarRouter {

	@Value("${marduk.shutdown.timeout:300}")
	private Long shutdownTimeout;

    private static Logger logger = LoggerFactory.getLogger(App.class);

    // must have a main method spring-boot can run
    public static void main(String... args) {
        logger.info("Starting Marduk...");
        
        configureJsonPath();
        
        FatJarRouter.main(args);
    }

	@Override
	public void configure() throws Exception {
		super.configure();
		getContext().getShutdownStrategy().setTimeout(shutdownTimeout);
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

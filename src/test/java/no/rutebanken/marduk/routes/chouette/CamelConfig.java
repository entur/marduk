package no.rutebanken.marduk.routes.chouette;

import java.io.IOException;

import org.apache.camel.spring.javaconfig.CamelConfiguration;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@Configuration
@ComponentScan({ "no.rutebanken.marduk.routes.chouette", "no.rutebanken.marduk.routes.blobstore",
		"no.rutebanken.marduk.repository", "no.rutebanken.marduk.routes.status", "no.rutebanken.marduk.routes.file",
		"no.rutebanken.marduk.config" })
public class CamelConfig extends CamelConfiguration {
	@Bean
	public PropertyPlaceholderConfigurer propertyPlaceholderConfigurer() throws IOException {
		final PropertyPlaceholderConfigurer ppc = new PropertyPlaceholderConfigurer();
		ppc.setLocations(ArrayUtils
				.addAll(new PathMatchingResourcePatternResolver().getResources("classpath*:application.properties")));

		return ppc;
	}

}

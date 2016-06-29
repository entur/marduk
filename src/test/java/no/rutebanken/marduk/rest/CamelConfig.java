package no.rutebanken.marduk.rest;

import java.io.IOException;

import org.apache.camel.CamelContext;
import org.apache.camel.PropertyInject;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.processor.interceptor.Tracer;
import org.apache.camel.spring.javaconfig.CamelConfiguration;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.context.TestPropertySource;

@Configuration
@ComponentScan({ "no.rutebanken.marduk.rest", "no.rutebanken.marduk.repository" })
public class CamelConfig extends CamelConfiguration {
	@Bean
	public PropertyPlaceholderConfigurer propertyPlaceholderConfigurer() throws IOException {
		final PropertyPlaceholderConfigurer ppc = new PropertyPlaceholderConfigurer();
		ppc.setLocations(ArrayUtils
				.addAll(new PathMatchingResourcePatternResolver().getResources("classpath*:application.properties")));

		return ppc;
	}

}

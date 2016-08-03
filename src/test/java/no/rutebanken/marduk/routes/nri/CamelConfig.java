package no.rutebanken.marduk.routes.nri;

import java.io.IOException;

import org.apache.camel.CamelContext;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.spring.javaconfig.CamelConfiguration;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@Configuration
@ComponentScan({ "no.rutebanken.marduk.routes.nri", "no.rutebanken.marduk.repository",
		"no.rutebanken.marduk.routes.blobstore", "no.rutebanken.marduk.config" })
public class CamelConfig extends CamelConfiguration {
	@Override
	protected void setupCamelContext(CamelContext camelContext) throws Exception {
		PropertiesComponent pc = new PropertiesComponent();
		pc.setLocation("classpath:application.properties");
		camelContext.addComponent("properties", pc);
		// see if trace logging is turned on
		super.setupCamelContext(camelContext);
	}

	@Bean
	public PropertyPlaceholderConfigurer propertyPlaceholderConfigurer() throws IOException {
		
		
		final PropertyPlaceholderConfigurer ppc = new PropertyPlaceholderConfigurer();
		ppc.setLocations(ArrayUtils
				.addAll(new PathMatchingResourcePatternResolver().getResources("classpath*:application.properties")));

		return ppc;
	}

}

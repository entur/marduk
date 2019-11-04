package no.rutebanken.marduk.config;

import org.eclipse.jetty.servlets.MultiPartFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RestConfiguration {

	// Substitute for using Camel .endpointProperty("enablemulti-partFilter", "true")
	@Bean
	public FilterRegistrationBean multiPartFilter() {
		FilterRegistrationBean mapping = new FilterRegistrationBean();
        mapping.setFilter(new MultiPartFilter());
        return mapping;
	}
}

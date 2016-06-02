package no.rutebanken.marduk.routes.nri;

import org.apache.camel.CamelContext;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.processor.interceptor.Tracer;
import org.apache.camel.spring.javaconfig.CamelConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("no.rutebanken.marduk.routes.nri")
public class CamelConfig extends CamelConfiguration {
	public CamelConfig() {
		super();
		// TODO Auto-generated constructor stub
	}

	// @Value("${logging.trace.enabled}")
	private Boolean tracingEnabled = Boolean.TRUE;

	@Override
	protected void setupCamelContext(CamelContext camelContext) throws Exception {
		PropertiesComponent pc = new PropertiesComponent();
		pc.setLocation("classpath:application.properties");
		camelContext.addComponent("properties", pc);
		// see if trace logging is turned on
		if (tracingEnabled) {
			camelContext.setTracing(true);
		}
		super.setupCamelContext(camelContext);
	}

	@Bean
	public Tracer camelTracer() {
		Tracer tracer = new Tracer();
		tracer.setTraceExceptions(false);
		tracer.setTraceInterceptors(true);
		tracer.setLogName("com.raibledesigns.camel.trace");
		return tracer;
	}
}

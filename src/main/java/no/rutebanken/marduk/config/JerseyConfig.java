package no.rutebanken.marduk.config;

import no.rutebanken.marduk.rest.ApplicationStatusResource;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.context.annotation.Configuration;

import javax.ws.rs.ApplicationPath;

@Configuration
@ApplicationPath("/jersey")
public class JerseyConfig extends ResourceConfig {

    public JerseyConfig() {
        register(ApplicationStatusResource.class);
    }

}

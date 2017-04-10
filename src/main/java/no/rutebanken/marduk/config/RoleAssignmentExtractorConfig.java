package no.rutebanken.marduk.config;

import org.rutebanken.helper.organisation.KeycloakRoleAssignmentExtractor;
import org.rutebanken.helper.organisation.RoleAssignmentExtractor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RoleAssignmentExtractorConfig {

    @Bean
    public RoleAssignmentExtractor keycloakRoleAssignmentExtractor() {
        return new KeycloakRoleAssignmentExtractor();
    }

}

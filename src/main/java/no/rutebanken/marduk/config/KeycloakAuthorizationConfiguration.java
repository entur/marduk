package no.rutebanken.marduk.config;

import org.rutebanken.helper.organisation.JwtRoleAssignmentExtractor;
import org.rutebanken.helper.organisation.RoleAssignmentExtractor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.security.AuthorizationService;

@Configuration
public class KeycloakAuthorizationConfiguration {

	@Bean
	public AuthorizationService service(ProviderRepository providerRepository, RoleAssignmentExtractor extractor) {
		return new AuthorizationService(providerRepository, extractor);
	}
	
	@Bean
	public RoleAssignmentExtractor extractor() {
		return new JwtRoleAssignmentExtractor();
	}
	
}

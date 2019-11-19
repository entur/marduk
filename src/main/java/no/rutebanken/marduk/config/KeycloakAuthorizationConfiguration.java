package no.rutebanken.marduk.config;

import org.entur.jwt.spring.entur.organisation.JwtRoleAssignmentExtractor;
import org.entur.jwt.spring.entur.organisation.RoleAssignmentExtractor;
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

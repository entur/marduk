package no.rutebanken.marduk.config;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.component.servlet.CamelHttpTransportServlet;
import org.apache.camel.component.spring.security.AuthenticationAdapter;
import org.apache.camel.component.spring.security.DefaultAuthenticationAdapter;
import org.apache.camel.component.spring.security.SpringSecurityAccessPolicy;
import org.apache.camel.component.spring.security.SpringSecurityAuthorizationPolicy;
import org.entur.jwt.spring.filter.JwtAuthorityMapper;
import org.entur.jwt.verifier.JwtVerifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.access.vote.AffirmativeBased;
import org.springframework.security.access.vote.AuthenticatedVoter;
import org.springframework.security.authentication.AuthenticationManager;

// http://fusetoolbox.blogspot.com/2015/06/camel-spring-security-and-oauth.html

@Configuration
public class JwtCamelSecurityConfig {

	private AuthenticationManager authenticationManager = new JwtAuthenticationManager();

	@Bean
	public SpringSecurityAuthorizationPolicy writeScopeAccessPolicy() {
		SpringSecurityAuthorizationPolicy policy = new SpringSecurityAuthorizationPolicy();
		policy.setAccessDecisionManager(accessDecisionManager());
		policy.setAuthenticationManager(authenticationManager);
		policy.setUseThreadSecurityContext(false); // rather set authentication on message

		SpringSecurityAccessPolicy p = new SpringSecurityAccessPolicy(AuthenticatedVoter.IS_AUTHENTICATED_FULLY);
		policy.setSpringSecurityAccessPolicy(p);

		return policy;
	}

	@Bean
	public AccessDecisionManager accessDecisionManager() {
		List<AccessDecisionVoter<?>> voters = new ArrayList<>();
		voters.add(authenticatedVoter());

		return new AffirmativeBased(voters);
	}

	@Bean
	public AuthenticatedVoter authenticatedVoter() {
		return new AuthenticatedVoter();
	}	

	@Bean
	public <T> JwtAuthenticationProcessor<T> jwtAuthenticationProcessor(JwtVerifier<T> verifier, JwtAuthorityMapper<T> authorityMapper) {
		return new JwtAuthenticationProcessor<>(verifier, authorityMapper);
	}

}

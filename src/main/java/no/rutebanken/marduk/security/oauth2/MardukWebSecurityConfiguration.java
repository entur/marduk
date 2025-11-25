package no.rutebanken.marduk.security.oauth2;

import org.entur.oauth2.multiissuer.MultiIssuerAuthenticationManagerResolver;
import org.entur.oauth2.multiissuer.MultiIssuerAuthenticationManagerResolverBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Authentication and authorization configuration for Marduk.
 * All requests must be authenticated except for the OpenAPI endpoint.
 * The OAuth2 ID-provider (Entur Partner Auth0) is identified using
 * {@link MultiIssuerAuthenticationManagerResolver} with multi-audience support.
 */
@Profile("!test")
@EnableWebSecurity
@Configuration
public class MardukWebSecurityConfiguration {

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedHeaders(Arrays.asList("Origin", "Accept", "X-Requested-With", "Content-Type", "Access-Control-Request-Method", "Access-Control-Request-Headers", "Authorization", "x-correlation-id", "Et-Client-Name", "baggage", "sentry-trace"));
        configuration.addAllowedOrigin("*");
        configuration.setAllowedMethods(Arrays.asList("GET", "PUT", "POST", "DELETE"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public MultiIssuerAuthenticationManagerResolver multiIssuerResolver(
            @Value("${marduk.oauth2.resourceserver.auth0.partner.jwt.audience}") String enturPartnerAuth0Audiences,
            @Value("${marduk.oauth2.resourceserver.auth0.partner.jwt.issuer-uri}") String enturPartnerAuth0Issuer) {

        return new MultiIssuerAuthenticationManagerResolverBuilder()
                .withEnturPartnerAuth0Audiences(Arrays.asList(enturPartnerAuth0Audiences.split(",")))
                .withEnturPartnerAuth0Issuer(enturPartnerAuth0Issuer)
                .build();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, MultiIssuerAuthenticationManagerResolver multiIssuerResolver) throws Exception {
        http.cors(withDefaults()).csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/services/openapi.yaml")).permitAll()
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/services/timetable_admin/openapi.yaml")).permitAll()
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/services/timetable-management/openapi.yaml")).permitAll()
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/actuator/prometheus")).permitAll()
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/actuator/health")).permitAll()
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/actuator/health/liveness")).permitAll()
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/actuator/health/readiness")).permitAll()
                        .anyRequest().authenticated()
                ).oauth2ResourceServer(configurer -> configurer.authenticationManagerResolver(multiIssuerResolver))
                .oauth2Client(withDefaults());
        return http.build();
    }

}

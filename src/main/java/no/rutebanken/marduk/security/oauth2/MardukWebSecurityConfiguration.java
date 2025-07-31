package no.rutebanken.marduk.security.oauth2;

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

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Authentication and authorization configuration for Marduk.
 * All requests must be authenticated except for the OpenAPI endpoint.
 * The Oauth2 ID-provider (Entur Partner Auth0 or RoR Auth0) is identified thanks to {@link MardukMultiIssuerAuthenticationManagerResolver}.
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
    public SecurityFilterChain filterChain(HttpSecurity http, MardukMultiIssuerAuthenticationManagerResolver mardukMultiIssuerAuthenticationManagerResolver) throws Exception {
        http.cors(withDefaults()).csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/services/openapi.json")).permitAll()
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/services/timetable_admin/openapi.json")).permitAll()
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/services/timetable-management/openapi.json")).permitAll()
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/actuator/prometheus")).permitAll()
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/actuator/health")).permitAll()
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/actuator/health/liveness")).permitAll()
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/actuator/health/readiness")).permitAll()
                        .anyRequest().authenticated()
                ).oauth2ResourceServer(configurer -> configurer.authenticationManagerResolver(mardukMultiIssuerAuthenticationManagerResolver))
                .oauth2Client(withDefaults());
        return http.build();
    }

}

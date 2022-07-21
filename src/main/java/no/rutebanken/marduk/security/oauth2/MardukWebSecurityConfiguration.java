package no.rutebanken.marduk.security.oauth2;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Authentication and authorization configuration for Marduk.
 * All requests must be authenticated except for the Swagger endpoint.
 * The Oauth2 ID-provider (Entur Partner Auth0 or RoR Auth0) is identified thanks to {@link MultiIssuerAuthenticationManagerResolver}.
 */
@Profile("!test")
@EnableWebSecurity
@Configuration
public class MardukWebSecurityConfiguration {

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedHeaders(Arrays.asList("Origin", "Accept", "X-Requested-With", "Content-Type", "Access-Control-Request-Method", "Access-Control-Request-Headers", "Authorization", "x-correlation-id"));
        configuration.addAllowedOrigin("*");
        configuration.setAllowedMethods(Arrays.asList("GET", "PUT", "POST", "DELETE"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, MultiIssuerAuthenticationManagerResolver multiIssuerAuthenticationManagerResolver) throws Exception {
        http.cors(withDefaults())
                .csrf().disable()
                .authorizeHttpRequests(authz -> authz
                        .antMatchers("/services/swagger.json").permitAll()
                        .antMatchers("/services/timetable_admin/swagger.json").permitAll()
                        // exposed internally only, on a different port (pod-level)
                        .antMatchers("/actuator/prometheus").permitAll()
                        .antMatchers("/actuator/health").permitAll()
                        .antMatchers("/actuator/health/liveness").permitAll()
                        .antMatchers("/actuator/health/readiness").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer().authenticationManagerResolver(multiIssuerAuthenticationManagerResolver)
                .and()
                .oauth2Client();
        return http.build();
    }

}

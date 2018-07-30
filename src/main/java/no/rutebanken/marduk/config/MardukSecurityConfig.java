
/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.config;

import org.keycloak.adapters.KeycloakConfigResolver;
import org.keycloak.adapters.springboot.KeycloakSpringBootConfigResolver;
import org.keycloak.adapters.springsecurity.KeycloakSecurityComponents;
import org.keycloak.adapters.springsecurity.config.KeycloakWebSecurityConfigurerAdapter;
import org.keycloak.adapters.springsecurity.filter.KeycloakAuthenticationProcessingFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static org.keycloak.adapters.springsecurity.filter.KeycloakAuthenticationProcessingFilter.AUTHORIZATION_HEADER;


@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@ComponentScan(basePackageClasses = KeycloakSecurityComponents.class)
public class MardukSecurityConfig extends KeycloakWebSecurityConfigurerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(MardukSecurityConfig.class);

    /**
     * Registers the KeycloakAuthenticationProvider with the authentication
     * manager.
     */
    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        auth.authenticationProvider(keycloakAuthenticationProvider());
    }

    /**
     * Defines the session authentication strategy.
     */
    @Bean
    @Override
    protected SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new RegisterSessionAuthenticationStrategy(new SessionRegistryImpl());
    }

    @Bean
    public KeycloakConfigResolver KeycloakConfigResolver() {
        return new KeycloakSpringBootConfigResolver();
    }


    /**
     * Override KeycloakAuthenticationProcessingFilter to support token as query param.
     *
     * Must:
     * - Add request matcher to enable the filter for requests that contain 'access_token' query parameter
     * - Treat such requests as those containing token as Authorization header. Thus is (somewhat dirty) achieved by overriding method
     * for checking whether request is of bearer token type to include query param token requests.
     *
     */
    @Override
    @Bean
    protected KeycloakAuthenticationProcessingFilter keycloakAuthenticationProcessingFilter() throws Exception {
        final RegexRequestMatcher tokenQueryParamMatcher = new RegexRequestMatcher("(.*?)access_token=(.*?)", null);
        RequestMatcher requestMatcher =
                new OrRequestMatcher(new RequestHeaderRequestMatcher(AUTHORIZATION_HEADER), tokenQueryParamMatcher);

        KeycloakAuthenticationProcessingFilter filter = new KeycloakAuthenticationProcessingFilter(authenticationManagerBean(), requestMatcher);

        filter.setSessionAuthenticationStrategy(sessionAuthenticationStrategy());
        return filter;
    }


    @Override
    protected void configure(HttpSecurity http) throws Exception {
        super.configure(http);

        logger.info("Configuring HttpSecurity");

        http.csrf().disable()
                .authorizeRequests()
                .anyRequest()
                .permitAll();
    }

}


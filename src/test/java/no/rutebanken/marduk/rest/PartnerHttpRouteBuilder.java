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

package no.rutebanken.marduk.rest;

import javax.ws.rs.NotFoundException;

import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.spring.security.SpringSecurityAuthorizationPolicy;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.entur.jwt.spring.camel.ExchangeJwtClaimExtractor;
import org.entur.jwt.spring.camel.JwtAuthenticationProcessor;
import org.entur.jwt.spring.camel.JwtAuthenticationRoutePolicyFactory;
import org.entur.jwt.verifier.JwtClaimException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.security.AuthorizationService;

/**
 * Test REST service
 */
@Component
public class PartnerHttpRouteBuilder extends BaseRouteBuilder {

    private static final String PLAIN = "text/plain";

    @Value("${server.port}")
    public String port;

    @Value("${server.admin.host}")
    public String host;
    
    @Autowired
    @Qualifier("validTokenAccessPolicy")
    private SpringSecurityAuthorizationPolicy springSecurityAuthorizationPolicy;

    @Autowired
    private JwtAuthenticationProcessor jwtAuthenticationProcessor;

    @Override
    public void configure() throws Exception {
        super.configure();
        
        JwtAuthenticationRoutePolicyFactory factory = new JwtAuthenticationRoutePolicyFactory(jwtAuthenticationProcessor);
        
        getContext().addRoutePolicyFactory(factory);
        
        onException(CamelAuthorizationException.class)
	        .handled(true)
	        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401))
	        .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
	        .transform(exceptionMessage());

        onException(AccessDeniedException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(403))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                .transform(exceptionMessage());

        onException(BadCredentialsException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                .transform(exceptionMessage());

        onException(NotFoundException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                .transform(exceptionMessage());

        rest("/myPath")
	        .get("/{codespace}")
	        .description("Test route")
	        .param().name("codespace").type(RestParamType.path).description("Provider Codespace").dataType("string").endParam()
	        .produces(PLAIN)
	        .bindingMode(RestBindingMode.off)
	        .responseMessage().code(200).endResponseMessage()
	        .responseMessage().code(400).message("Invalid codespace").endResponseMessage()
	        .route()
            .policy(springSecurityAuthorizationPolicy)
	        .process(e -> {
	            try {
	            	Long organsiationId = ExchangeJwtClaimExtractor.extract(e, "organisationID", Long.class);
	            	e.getOut().setBody("My message for organsiation " + organsiationId);
	            } catch(JwtClaimException e1) {
	            	// whoops, no organisation id
	            	throw new AccessDeniedException("Expected token with organisation id");
	            }
	        })
	        .log(LoggingLevel.INFO, "Return simple response")
	        .removeHeaders("CamelHttp*")
	        .routeId("test-my-path")
	        .endRest();

    }
}



package no.rutebanken.marduk.config;

import java.util.Set;

import javax.security.auth.Subject;

import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.DelegateProcessor;
import org.apache.camel.spi.AuthorizationPolicy;
import org.apache.camel.spi.RouteContext;
import org.entur.jwt.spring.filter.JwtAuthenticationToken;
import org.rutebanken.helper.organisation.AuthorizationClaim;
import org.springframework.security.core.Authentication;

import com.amazonaws.auth.policy.Principal;

import no.rutebanken.marduk.security.AuthorizationService;

public class AtLeastOneAuthorizationPolicy implements AuthorizationPolicy {

    private class AuthorizeDelegateProcess extends DelegateProcessor {
        
        AuthorizeDelegateProcess(Processor processor) {
            super(processor);
        }
        
        public void process(Exchange exchange) throws Exception {
            beforeProcess(exchange);
            processNext(exchange);
        }
        
    }
    
    private final AuthorizationService authorizationService;
    private final AuthorizationClaim[] claims;
    
	public AtLeastOneAuthorizationPolicy(AuthorizationService authorizationService, AuthorizationClaim ... claims) {
		super();
		this.authorizationService = authorizationService;
		this.claims = claims;
	}

	@Override
	public void beforeWrap(RouteContext routeContext, ProcessorDefinition<?> definition) {
	}

    public Processor wrap(RouteContext routeContext, Processor processor) {
        // wrap the processor with authorizeDelegateProcessor
        return new AuthorizeDelegateProcess(processor);
    }

    protected void beforeProcess(Exchange exchange) throws Exception {
        Authentication authentication = getAuthentication(exchange.getIn());
        if (authentication == null) {
            CamelAuthorizationException authorizationException =
                new CamelAuthorizationException("Cannot find the Authentication instance.", exchange);
            throw authorizationException;
        }
        
        authorizationService.verifyAtLeastOne(authentication, claims);
    }
    
    protected Authentication getAuthentication(Message message) {
        Subject subject = message.getHeader(Exchange.AUTHENTICATION, Subject.class);
        if (subject != null) {
        	
        	Set<JwtAuthenticationToken> principals = subject.getPrincipals(JwtAuthenticationToken.class);
        	if(!principals.isEmpty()) {
        		return principals.iterator().next();
        	}
        }
        return null;
    }    
}

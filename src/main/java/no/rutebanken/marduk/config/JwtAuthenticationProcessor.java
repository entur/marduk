package no.rutebanken.marduk.config;

import java.util.List;

import javax.security.auth.Subject;
import javax.servlet.http.HttpServletRequest;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.entur.jwt.jwk.SigningKeyUnavailableException;
import org.entur.jwt.spring.filter.JwtAuthenticationFilter;
import org.entur.jwt.spring.filter.JwtAuthenticationServiceUnavailableException;
import org.entur.jwt.spring.filter.JwtAuthenticationToken;
import org.entur.jwt.spring.filter.JwtAuthorityMapper;
import org.entur.jwt.verifier.JwtVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;

/**
 * A {@linkplain Processor} which, if present, extracts the Json Web Token from
 * the message {@linkplain HttpServletRequest} Authorization header and saves it to the message
 * {@linkplain Exchange#AUTHENTICATION}. If not present, an anonymous authentication object is
 * used.
 * <br>
 * This implementation assumes that {@linkplain SpringSecurityAuthorizationPolicy#setUseThreadSecurityContext}
 * is used to disable thread local authentication.
 * 
 * @param <T> token type
 */

public class JwtAuthenticationProcessor<T> implements Processor {

	private static Authentication anonymous = new AnonymousAuthenticationToken("anonymous", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

	public static final String AUTHORIZATION = "Authorization";
	
    private final JwtVerifier<T> verifier;
    private final JwtAuthorityMapper<T> authorityMapper;

	public JwtAuthenticationProcessor(JwtVerifier<T> verifier, JwtAuthorityMapper<T> authorityMapper) {
		super();
		this.verifier = verifier;
		this.authorityMapper = authorityMapper;
	}

	@Override
	public void process(Exchange exchange) {
		// implementation note: Must add anon authentication or else SpringSecurityAuthorizationPolicy blows up
		
		Message in = exchange.getIn();
		if(in.getHeader(Exchange.AUTHENTICATION) == null) {
			// https://camel.apache.org/components/latest/http-component.html
			HttpServletRequest request = in.getBody(HttpServletRequest.class);
			
			Authentication authentication;
			if(request != null) {
		        String header = request.getHeader(AUTHORIZATION);
		        if (header != null) {
		        	// if a token is present, it must be valid regardless of whether the end-point requires authorization or not
		        	T token;
		        	try {
		        		token = verifier.verify(header); // note: can return null
		        	} catch(SigningKeyUnavailableException e) {
		        		throw new JwtAuthenticationServiceUnavailableException("Unable to obtain certificates for checking token signature", e);
		        	}
		            if(token != null) {
		                List<GrantedAuthority> authorities = authorityMapper.getGrantedAuthorities(token);
		
		                authentication = new JwtAuthenticationToken<T>(token, header, authorities);
		            } else {
		                throw new BadCredentialsException("Unknown issuer");
		            }
		        } else {
					authentication = anonymous;
		        }
			} else {
				authentication = anonymous;
			}
    		Subject subject = new Subject();
    		subject.getPrincipals().add(authentication);
    		in.setHeader(Exchange.AUTHENTICATION, subject);
		}
	}

	
}
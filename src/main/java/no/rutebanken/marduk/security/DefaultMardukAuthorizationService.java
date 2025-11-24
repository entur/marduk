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

package no.rutebanken.marduk.security;


import jakarta.servlet.http.HttpServletRequest;
import org.apache.camel.Exchange;
import org.apache.camel.component.platform.http.springboot.PlatformHttpMessage;
import org.apache.hc.core5.http.HttpHeaders;
import org.rutebanken.helper.organisation.authorization.AuthorizationService;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

public class DefaultMardukAuthorizationService implements MardukAuthorizationService {

    private final AuthorizationService<Long> authorizationService;
    private final AuthenticationManagerResolver<HttpServletRequest> resolver;

    public DefaultMardukAuthorizationService(AuthorizationService<Long> authorizationService, AuthenticationManagerResolver<HttpServletRequest> resolver) {
        this.authorizationService = authorizationService;
        this.resolver = resolver;
    }


    @Override
    public void verifyAdministratorPrivileges(Exchange e) {
        executeWithSecurityContext(e, () -> authorizationService.validateRouteDataAdmin());
    }

    @Override
    public void verifyRouteDataEditorPrivileges(Long providerId, Exchange e) {
        executeWithSecurityContext(e, () -> authorizationService.validateEditRouteData(providerId));
    }

    @Override
    public void verifyBlockViewerPrivileges(Long providerId, Exchange e) {
        executeWithSecurityContext(e, () -> authorizationService.validateViewBlockData(providerId));
    }

    private void executeWithSecurityContext(Exchange e, Runnable authorizationCheck) {
        boolean contextSet = setSecurityContext(e);
        try {
            authorizationCheck.run();
        } finally {
            if (contextSet) {
                SecurityContextHolder.clearContext();
            }
        }
    }


    /**
     * The Camel component platform-http uses ASync HTTP and does not copy the Spring Security context in the worker thread.
     * When entering the REST route, the security context must be rebuilt from the Authorization header.
     * @param e the Camel exchange
     * @return true if the security context was set by this method, false otherwise
     */
    private boolean setSecurityContext(Exchange e) {
        if (SecurityContextHolder.getContext().getAuthentication() == null && e.getIn() instanceof PlatformHttpMessage platformHttpMessage) {
            String encodedToken = e.getIn().getHeader(HttpHeaders.AUTHORIZATION, "", String.class).replace("Bearer ", "");
            if(!encodedToken.isEmpty()) {
                BearerTokenAuthenticationToken bearer = new BearerTokenAuthenticationToken(encodedToken);
                Authentication authentication = resolver.resolve(platformHttpMessage.getRequest()).authenticate(bearer);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                return true;
            }
        }
        return false;
    }

}

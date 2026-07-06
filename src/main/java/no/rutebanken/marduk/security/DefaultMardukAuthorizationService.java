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
import org.apache.camel.support.SynchronizationAdapter;
import org.rutebanken.helper.organisation.authorization.AuthorizationService;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

public class DefaultMardukAuthorizationService implements MardukAuthorizationService {

    private final AuthorizationService<Long> authorizationService;
    private final AuthenticationManagerResolver<HttpServletRequest> resolver;

    public DefaultMardukAuthorizationService(AuthorizationService<Long> authorizationService,
                                             AuthenticationManagerResolver<HttpServletRequest> resolver) {
        this.authorizationService = authorizationService;
        this.resolver = resolver;
    }

    @Override
    public void verifyAdministratorPrivileges() {
        authorizationService.validateRouteDataAdmin();
    }

    @Override
    public void verifyAdministratorPrivileges(Exchange exchange) {
        executeWithSecurityContext(exchange, this::verifyAdministratorPrivileges);
    }

    @Override
    public void verifyRouteDataEditorPrivileges(Long providerId) {
        authorizationService.validateEditRouteData(providerId);
    }

    @Override
    public void verifyRouteDataEditorPrivileges(Long providerId, Exchange exchange) {
        executeWithSecurityContext(exchange, () -> verifyRouteDataEditorPrivileges(providerId));
    }

    @Override
    public void verifyBlockViewerPrivileges(Long providerId) {
        authorizationService.validateViewBlockData(providerId);
    }

    @Override
    public void verifyBlockViewerPrivileges(Long providerId, Exchange exchange) {
        executeWithSecurityContext(exchange, () -> verifyBlockViewerPrivileges(providerId));
    }

    private void executeWithSecurityContext(Exchange exchange, Runnable authorizationCheck) {
        if (setSecurityContext(exchange)) {
            // Clear the rebuilt context when the exchange completes, not immediately after the check:
            // downstream synchronous steps on this worker thread (e.g. direct:setUsername, which reads
            // the principal via UsernameService) must still see it. The context is still torn down before
            // the pooled worker thread serves the next request. allowHandover() is false so the clear
            // stays pinned to this thread even if the exchange is later handed to another one.
            exchange.getUnitOfWork().addSynchronization(new SynchronizationAdapter() {
                @Override
                public void onDone(Exchange e) {
                    SecurityContextHolder.clearContext();
                }

                @Override
                public boolean allowHandover() {
                    return false;
                }
            });
        }
        authorizationCheck.run();
    }

    /**
     * The Camel platform-http component processes requests on an asynchronous worker thread that does
     * not carry the Spring Security context held in the request thread's {@link SecurityContextHolder}.
     * When entering a REST route the security context must therefore be rebuilt from the Authorization
     * header.
     *
     * @return true if the security context was set by this method, false otherwise
     */
    private boolean setSecurityContext(Exchange exchange) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return false;
        }
        if (!(exchange.getIn() instanceof PlatformHttpMessage platformHttpMessage)) {
            return false;
        }
        String encodedToken = exchange.getIn()
                .getHeader(HttpHeaders.AUTHORIZATION, "", String.class)
                .replace("Bearer ", "");
        if (encodedToken.isEmpty()) {
            return false;
        }
        if (resolver == null) {
            // Only reachable for a real platform-http request that carries a bearer token: the earlier
            // guards already returned for non-platform-http exchanges and missing tokens. A null resolver
            // here means the bean is misconfigured, so fail loudly instead of running the authorization
            // check without a principal.
            throw new IllegalStateException("No AuthenticationManagerResolver configured: cannot rebuild the Spring Security context for a platform-http request");
        }
        BearerTokenAuthenticationToken bearer = new BearerTokenAuthenticationToken(encodedToken);
        Authentication authentication = resolver.resolve(platformHttpMessage.getRequest()).authenticate(bearer);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return true;
    }

}

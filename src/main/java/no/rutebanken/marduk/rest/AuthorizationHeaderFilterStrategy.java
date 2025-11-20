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

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * {@link org.apache.camel.spi.HeaderFilterStrategy} that prevents sensitive HTTP request headers from
 * being propagated back to the client in HTTP responses while still making
 * them available to Camel routes for authorization checks.
 */
@Component("authorizationHeaderFilterStrategy")
public class AuthorizationHeaderFilterStrategy extends DefaultHeaderFilterStrategy {

    private static final String AUTHORIZATION_HEADER = HttpHeaders.AUTHORIZATION;

    @Override
    public boolean applyFilterToExternalHeaders(String headerName, Object headerValue, Exchange exchange) {
        if (isAuthorizationHeader(headerName)) {
            return true;
        }
        return super.applyFilterToExternalHeaders(headerName, headerValue, exchange);
    }

    @Override
    public boolean applyFilterToCamelHeaders(String headerName, Object headerValue, Exchange exchange) {
        if (isAuthorizationHeader(headerName)) {
            return false;
        }
        return super.applyFilterToCamelHeaders(headerName, headerValue, exchange);
    }

    private static boolean isAuthorizationHeader(String headerName) {
        return headerName != null && AUTHORIZATION_HEADER.equalsIgnoreCase(headerName);
    }
}

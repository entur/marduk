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

package no.rutebanken.marduk;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

public class Utils {

    private  Utils() {
    }

    public static Long getLastPathElementOfUrl(String url) {
        if (url == null) {
            throw new IllegalArgumentException("Url is null");
        }
        return Long.valueOf(url.substring(url.lastIndexOf('/') + 1));
    }

    public static String getUsername() {
        String user = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() != null && auth.getPrincipal() instanceof Jwt) {
            user = ((Jwt) auth.getPrincipal()).getClaimAsString("preferred_username");
        }
        return (user == null) ? "unknown" : user;
    }
}

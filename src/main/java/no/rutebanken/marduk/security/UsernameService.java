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

import org.rutebanken.helper.organisation.user.UserInfoExtractor;
import org.springframework.stereotype.Service;

/**
 * Service for extracting username from the security context.
 */
@Service
public class UsernameService {

    private final UserInfoExtractor userInfoExtractor;

    public UsernameService(UserInfoExtractor userInfoExtractor) {
        this.userInfoExtractor = userInfoExtractor;
    }

    /**
     * Get the preferred username from the current security context.
     *
     * @return the preferred username, or "unknown" if not available
     */
    public String getPreferredUsername() {
        String preferredUsername = userInfoExtractor.getPreferredUsername();
        if (preferredUsername == null) {
            preferredUsername = "unknown";
        }
        return preferredUsername;
    }
}

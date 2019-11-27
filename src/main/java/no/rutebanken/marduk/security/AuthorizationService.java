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

import java.util.List;

import org.entur.jwt.spring.entur.organisation.AbstractAuthorizationService;
import org.entur.jwt.spring.entur.organisation.AuthorizationClaim;
import org.entur.jwt.spring.entur.organisation.RoleAssignment;
import org.entur.jwt.spring.entur.organisation.RoleAssignmentExtractor;

import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.ProviderRepository;

public class AuthorizationService extends AbstractAuthorizationService {

	private ProviderRepository providerRepository;
	
	public AuthorizationService(ProviderRepository providerRepository, RoleAssignmentExtractor roleAssignmentExtractor) {
		super(roleAssignmentExtractor);
		
		this.providerRepository = providerRepository;
	}

    public boolean hasRoleForProvider(List<RoleAssignment> roleAssignments, AuthorizationClaim claim) {

        Provider provider = providerRepository.getProvider(claim.getProviderId());
        if (provider == null) {
            return false;
        }

        return roleAssignments.stream()
                       .filter(ra -> claim.getRequiredRole().equals(ra.getRole())).anyMatch(ra -> provider.chouetteInfo.xmlns.equals(ra.getOrganisation()));

    }
}

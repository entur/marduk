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

import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.security.DefaultMardukAuthorizationService;
import no.rutebanken.marduk.security.MardukAuthorizationService;
import no.rutebanken.marduk.security.permissionstore.PermissionStoreClient;
import no.rutebanken.marduk.security.permissionstore.PermissionStoreRestRoleAssignmentExtractor;
import org.rutebanken.helper.organisation.AuthorizationConstants;
import org.rutebanken.helper.organisation.RoleAssignmentExtractor;
import org.rutebanken.helper.organisation.authorization.AuthorizationService;
import org.rutebanken.helper.organisation.authorization.DefaultAuthorizationService;
import org.rutebanken.helper.organisation.authorization.FullAccessAuthorizationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * Configure authorization.
 */
@Configuration
public class AuthorizationConfig {

    @Bean
    public RoleAssignmentExtractor roleAssignmentExtractor(
            PermissionStoreClient permissionStoreClient
    ) {
        return new PermissionStoreRestRoleAssignmentExtractor(
                permissionStoreClient,
                "marduk",
                Set.of(AuthorizationConstants.ROLE_ROUTE_DATA_EDIT,
                        AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN,
                        AuthorizationConstants.ROLE_ORGANISATION_EDIT,
                        AuthorizationConstants.ROLE_NETEX_BLOCKS_DATA_VIEW
                ));
    }


    /**
     * OAuth2 token-based authorization service.
     * The mapping function matches the Baba provider id to the organization codespace.
     * The ChouetteInfo.referential property is used instead of the ChouetteInfo.xmlns property for the mapping:
     * this allows for filtering out the RB referentials (referentials prefixed by RB_).
     */
    @ConditionalOnProperty(
            value = "marduk.security.authorization-service",
            havingValue = "token-based"
    )
    @Bean("authorizationService")
    public AuthorizationService<Long> tokenBasedAuthorizationService(ProviderRepository providerRepository, RoleAssignmentExtractor roleAssignmentExtractor) {
        return new DefaultAuthorizationService<>(
                providerId -> providerRepository.getProvider(providerId) == null ? null : providerRepository.getProvider(providerId).getChouetteInfo().getReferential().toUpperCase(),
                roleAssignmentExtractor
        );
    }

    @ConditionalOnProperty(
            value = "marduk.security.authorization-service",
            havingValue = "full-access"
    )
    @Bean("authorizationService")
    public AuthorizationService<Long> fullAccessAuthorizationService() {
        return new FullAccessAuthorizationService();
    }


    @Bean
    public MardukAuthorizationService mardukAuthorizationService(AuthorizationService<Long> authorizationService) {
        return new DefaultMardukAuthorizationService(authorizationService);
    }

}



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

import org.rutebanken.helper.organisation.AuthorizationConstants;
import org.rutebanken.helper.organisation.RoleAssignment;

import java.util.ArrayList;
import java.util.List;

class RoleAssignmentListBuilder {

    private final List<RoleAssignment> roleAssignments = new ArrayList<>();

    public static RoleAssignmentListBuilder builder() {
        return new RoleAssignmentListBuilder();
    }

    public List<RoleAssignment> build() {
        return roleAssignments;
    }

    public RoleAssignmentListBuilder withAccessAllAreas() {
        return withRoleForProvider(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN, "RB");
    }


    public RoleAssignmentListBuilder withRoleForProvider(String role, String providerXmlns) {
        RoleAssignment roleForProvider = RoleAssignment.builder().withRole(role)
                                                 .withOrganisation(providerXmlns).build();

        roleAssignments.add(roleForProvider);
        return this;
    }


}

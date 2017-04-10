package no.rutebanken.marduk.security;

import org.rutebanken.helper.organisation.AuthorizationConstants;
import org.rutebanken.helper.organisation.RoleAssignment;

import java.util.ArrayList;
import java.util.List;

public class RoleAssignmentListBuilder {

    private List<RoleAssignment> roleAssignments = new ArrayList<>();

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

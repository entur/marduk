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

package no.rutebanken.marduk.security.oauth2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.rutebanken.helper.organisation.RoleAssignment;
import org.rutebanken.helper.organisation.RoleAssignmentExtractor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Extract RoleAssignments from JwtAuthenticationToken.
 */
public class JwtRoleAssignmentExtractor implements RoleAssignmentExtractor {

    private static final String ATTRIBUTE_NAME_ROLE_ASSIGNMENT = "roles";
    private static ObjectMapper mapper = new ObjectMapper();

    public List<RoleAssignment> getRoleAssignmentsForUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return getRoleAssignmentsForUser(auth);
    }

    @Override
    public List<RoleAssignment> getRoleAssignmentsForUser(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken) {
            JwtAuthenticationToken jwtAuthenticationToken = (JwtAuthenticationToken) auth;
            Jwt jwt = (Jwt) jwtAuthenticationToken.getPrincipal();
            Object claim = jwt.getClaim(ATTRIBUTE_NAME_ROLE_ASSIGNMENT);
            if (claim == null) {
                return new ArrayList<>();
            }
            List<Object> roleAssignmentList;
            if (claim instanceof List) {
                roleAssignmentList = (List) claim;
            } else if (claim instanceof String) {
                roleAssignmentList = Arrays.asList(((String) claim).split("##"));
            } else {
                throw new IllegalArgumentException("Unsupported 'roles' claim type: " + claim);
            }

            return roleAssignmentList.stream().map(JwtRoleAssignmentExtractor::parse).collect(Collectors.toList());
        } else {
            throw new AccessDeniedException("Not authenticated with token");
        }
    }

    private static RoleAssignment parse(Object roleAssignment) {
        // dual value support because of corresponding legacy code
        if (roleAssignment instanceof Map) {
            return mapper.convertValue(roleAssignment, RoleAssignment.class);
        }
        try {
            return mapper.readValue((String) roleAssignment, RoleAssignment.class);
        } catch (IOException ioE) {
            throw new IllegalArgumentException("Exception while parsing role assignments from JSON: " + ioE.getMessage(), ioE);
        }
    }
}

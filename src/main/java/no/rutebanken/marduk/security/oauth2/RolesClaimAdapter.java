package no.rutebanken.marduk.security.oauth2;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.rutebanken.helper.organisation.AuthorizationConstants;
import org.rutebanken.helper.organisation.RoleAssignment;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class RolesClaimAdapter implements Converter<Map<String, Object>, Map<String, Object>> {

    private final MappedJwtClaimSetConverter delegate =
            MappedJwtClaimSetConverter.withDefaults(Collections.emptyMap());

    private static ObjectMapper mapper = new ObjectMapper();

    private static Map<Long, String> rutebankenOrganisations = Map.of(1L, "RB");


    public Map<String, Object> convert(Map<String, Object> claims) {
        Map<String, Object> convertedClaims = this.delegate.convert(claims);

        Long enturOrganisationId = (Long) convertedClaims.get("https://entur.io/organisationID");
        String rutebankenOrganisationId = getRutebankenOrganisationId(enturOrganisationId);
        RoleAssignment.Builder builder = RoleAssignment.builder();
        builder.withRole(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN);
        builder.withOrganisation(rutebankenOrganisationId);
        List<String> roleAssignments = Arrays.asList(toJSON(builder.build()));
        convertedClaims.put("roles", roleAssignments);
        return convertedClaims;
    }

    private String getRutebankenOrganisationId(Long enturOrganisationId) {
        return Optional.ofNullable(rutebankenOrganisations.get(enturOrganisationId))
                .orElseThrow(() -> new IllegalArgumentException("unknown organisation " + enturOrganisationId));
    }


    private String toJSON(RoleAssignment roleAssignment) {
        StringWriter writer = new StringWriter();
        try {
            mapper.writeValue(writer, roleAssignment);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}

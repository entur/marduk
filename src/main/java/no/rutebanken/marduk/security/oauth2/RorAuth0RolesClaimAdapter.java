package no.rutebanken.marduk.security.oauth2;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;


/**
 * Insert a "roles" claim in the JWT token based on the organisationID claim, for compatibility with the existing
 * authorization process (@{@link JwtRoleAssignmentExtractor}).
 */
@Component
public class RorAuth0RolesClaimAdapter implements Converter<Map<String, Object>, Map<String, Object>> {

    private final MappedJwtClaimSetConverter delegate =
            MappedJwtClaimSetConverter.withDefaults(Collections.emptyMap());

    @Override
    public Map<String, Object> convert(Map<String, Object> claims) {
        return this.delegate.convert(claims);
    }


}

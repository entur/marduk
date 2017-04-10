package no.rutebanken.marduk.security;

public class AuthorizationClaim {
    private String requiredRole;
    private Long providerId;

    public AuthorizationClaim(String requiredRole, Long providerId) {
        this.requiredRole = requiredRole;
        this.providerId = providerId;
    }

    public AuthorizationClaim(String requiredRole) {
        this.requiredRole = requiredRole;
    }

    public String getRequiredRole() {
        return requiredRole;
    }

    public Long getProviderId() {
        return providerId;
    }
}

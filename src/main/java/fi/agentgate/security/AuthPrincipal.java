package fi.agentgate.security;

import java.util.UUID;

import fi.agentgate.user.Role;

/** The authenticated caller, rebuilt from the JWT on every request. */
public record AuthPrincipal(UUID id, String username, Role role) {

    public boolean isAgent() {
        return role == Role.AGENT;
    }
}

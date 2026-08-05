package io.github.yanziki.enterpriseai.identity;

import java.util.Set;

public record AuthenticatedUser(String subject, Set<String> platformRoles) {

    public boolean hasPlatformRole(String role) {
        return platformRoles.contains(role);
    }
}

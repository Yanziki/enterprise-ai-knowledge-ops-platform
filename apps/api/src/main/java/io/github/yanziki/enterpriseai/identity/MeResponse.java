package io.github.yanziki.enterpriseai.identity;

import java.util.List;
import java.util.UUID;

public record MeResponse(
        String subject,
        String email,
        String displayName,
        List<String> platformRoles,
        List<OrganizationMembershipResponse> organizations) {

    public record OrganizationMembershipResponse(
            UUID id,
            String slug,
            String displayName,
            String role,
            List<WorkspaceResponse> workspaces) {}

    public record WorkspaceResponse(UUID id, String slug, String displayName) {}
}

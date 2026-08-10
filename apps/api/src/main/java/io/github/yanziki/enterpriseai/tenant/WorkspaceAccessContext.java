package io.github.yanziki.enterpriseai.tenant;

import io.github.yanziki.enterpriseai.identity.AuthenticatedUser;
import java.util.UUID;

public record WorkspaceAccessContext(
        AuthenticatedUser authenticatedUser,
        UUID organizationId,
        String organizationSlug,
        String organizationDisplayName,
        UUID workspaceId,
        String workspaceSlug,
        String workspaceDisplayName,
        WorkspaceAccessRole accessRole) {}

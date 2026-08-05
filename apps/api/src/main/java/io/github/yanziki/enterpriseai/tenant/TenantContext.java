package io.github.yanziki.enterpriseai.tenant;

import io.github.yanziki.enterpriseai.identity.AuthenticatedUser;
import java.util.UUID;

public record TenantContext(
        AuthenticatedUser authenticatedUser,
        UUID organizationId,
        String organizationSlug,
        String organizationDisplayName) {}

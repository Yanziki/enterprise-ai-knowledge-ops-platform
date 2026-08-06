package io.github.yanziki.enterpriseai.tenant;

import java.util.UUID;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationSummaryController {

    private final TenantAuthorizationService tenantAuthorizationService;
    private final WorkspaceRepository workspaceRepository;

    public OrganizationSummaryController(
            TenantAuthorizationService tenantAuthorizationService,
            WorkspaceRepository workspaceRepository) {
        this.tenantAuthorizationService = tenantAuthorizationService;
        this.workspaceRepository = workspaceRepository;
    }

    @GetMapping("/{organizationSlug}/summary")
    OrganizationSummaryResponse summary(
            @PathVariable String organizationSlug, JwtAuthenticationToken authentication) {
        TenantContext tenantContext =
                tenantAuthorizationService.requireAccess(authentication, organizationSlug);
        return new OrganizationSummaryResponse(
                tenantContext.organizationId(),
                tenantContext.organizationSlug(),
                tenantContext.organizationDisplayName(),
                workspaceRepository.countByOrganizationId(tenantContext.organizationId()));
    }

    record OrganizationSummaryResponse(
            UUID id, String slug, String displayName, long workspaceCount) {}
}

package io.github.yanziki.enterpriseai.shared.system;

import io.github.yanziki.enterpriseai.identity.AuthenticatedUserResolver;
import io.github.yanziki.enterpriseai.identity.MembershipRepository;
import io.github.yanziki.enterpriseai.identity.UserProfileRepository;
import io.github.yanziki.enterpriseai.tenant.OrganizationRepository;
import io.github.yanziki.enterpriseai.tenant.WorkspaceRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminSystemSummaryController {

    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final OrganizationRepository organizationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserProfileRepository userProfileRepository;
    private final MembershipRepository membershipRepository;

    public AdminSystemSummaryController(
            AuthenticatedUserResolver authenticatedUserResolver,
            OrganizationRepository organizationRepository,
            WorkspaceRepository workspaceRepository,
            UserProfileRepository userProfileRepository,
            MembershipRepository membershipRepository) {
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.organizationRepository = organizationRepository;
        this.workspaceRepository = workspaceRepository;
        this.userProfileRepository = userProfileRepository;
        this.membershipRepository = membershipRepository;
    }

    @GetMapping("/system-summary")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    AdminSystemSummaryResponse summary(JwtAuthenticationToken authentication) {
        authenticatedUserResolver.resolve(authentication);
        return new AdminSystemSummaryResponse(
                organizationRepository.count(),
                workspaceRepository.count(),
                userProfileRepository.count(),
                membershipRepository.count());
    }

    record AdminSystemSummaryResponse(
            long organizationCount,
            long workspaceCount,
            long userProfileCount,
            long membershipCount) {}
}

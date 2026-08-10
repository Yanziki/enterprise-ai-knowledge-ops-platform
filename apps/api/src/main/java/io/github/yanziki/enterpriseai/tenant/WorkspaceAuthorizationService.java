package io.github.yanziki.enterpriseai.tenant;

import io.github.yanziki.enterpriseai.identity.AuthenticatedUser;
import io.github.yanziki.enterpriseai.identity.AuthenticatedUserResolver;
import io.github.yanziki.enterpriseai.identity.Membership;
import io.github.yanziki.enterpriseai.identity.MembershipRepository;
import io.github.yanziki.enterpriseai.identity.MembershipRole;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkspaceAuthorizationService {

    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final OrganizationRepository organizationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final MembershipRepository membershipRepository;

    public WorkspaceAuthorizationService(
            AuthenticatedUserResolver authenticatedUserResolver,
            OrganizationRepository organizationRepository,
            WorkspaceRepository workspaceRepository,
            MembershipRepository membershipRepository) {
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.organizationRepository = organizationRepository;
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public WorkspaceAccessContext requireAccess(
            JwtAuthenticationToken authentication,
            String organizationSlug,
            String workspaceSlug,
            WorkspaceOperation operation) {
        AuthenticatedUser user = authenticatedUserResolver.resolve(authentication);
        Organization organization =
                organizationRepository
                        .findBySlug(organizationSlug)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Organization was not found"));
        Workspace workspace =
                workspaceRepository
                        .findByOrganizationIdAndSlug(organization.getId(), workspaceSlug)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Workspace was not found"));

        WorkspaceAccessRole accessRole = resolveAccessRole(user, organization, workspace);
        if (!accessRole.permits(operation)) {
            throw new AccessDeniedException("Workspace operation is denied");
        }

        return new WorkspaceAccessContext(
                user,
                organization.getId(),
                organization.getSlug(),
                organization.getDisplayName(),
                workspace.getId(),
                workspace.getSlug(),
                workspace.getDisplayName(),
                accessRole);
    }

    private WorkspaceAccessRole resolveAccessRole(
            AuthenticatedUser user, Organization organization, Workspace workspace) {
        if (user.hasPlatformRole("PLATFORM_ADMIN")) {
            return WorkspaceAccessRole.PLATFORM_ADMIN;
        }

        List<Membership> applicableMemberships =
                membershipRepository.findApplicableWorkspaceMemberships(
                        user.subject(), organization.getId(), workspace.getId());
        WorkspaceAccessRole membershipRole =
                applicableMemberships.stream()
                        .map(Membership::getRole)
                        .map(WorkspaceAuthorizationService::toAccessRole)
                        .max(Comparator.comparingInt(WorkspaceAccessRole::privilege))
                        .orElseThrow(() -> new AccessDeniedException("Workspace access is denied"));
        WorkspaceAccessRole platformCapability =
                user.platformRoles().stream()
                        .map(WorkspaceAuthorizationService::toAccessRole)
                        .filter(java.util.Objects::nonNull)
                        .max(Comparator.comparingInt(WorkspaceAccessRole::privilege))
                        .orElseThrow(() -> new AccessDeniedException("Workspace role is denied"));

        return membershipRole.privilege() <= platformCapability.privilege()
                ? membershipRole
                : platformCapability;
    }

    private static WorkspaceAccessRole toAccessRole(MembershipRole role) {
        return WorkspaceAccessRole.valueOf(role.name());
    }

    private static WorkspaceAccessRole toAccessRole(String role) {
        try {
            return WorkspaceAccessRole.valueOf(role);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}

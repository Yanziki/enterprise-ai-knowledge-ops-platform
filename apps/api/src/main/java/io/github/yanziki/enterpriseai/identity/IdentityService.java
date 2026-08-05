package io.github.yanziki.enterpriseai.identity;

import io.github.yanziki.enterpriseai.identity.MeResponse.OrganizationMembershipResponse;
import io.github.yanziki.enterpriseai.identity.MeResponse.WorkspaceResponse;
import io.github.yanziki.enterpriseai.tenant.Organization;
import io.github.yanziki.enterpriseai.tenant.Workspace;
import io.github.yanziki.enterpriseai.tenant.WorkspaceRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {

    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final UserProfileRepository userProfileRepository;
    private final MembershipRepository membershipRepository;
    private final WorkspaceRepository workspaceRepository;

    public IdentityService(
            AuthenticatedUserResolver authenticatedUserResolver,
            UserProfileRepository userProfileRepository,
            MembershipRepository membershipRepository,
            WorkspaceRepository workspaceRepository) {
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.userProfileRepository = userProfileRepository;
        this.membershipRepository = membershipRepository;
        this.workspaceRepository = workspaceRepository;
    }

    @Transactional(readOnly = true)
    public MeResponse getCurrentUser(JwtAuthenticationToken authentication) {
        AuthenticatedUser authenticatedUser = authenticatedUserResolver.resolve(authentication);
        UserProfile profile =
                userProfileRepository
                        .findByIdentitySubject(authenticatedUser.subject())
                        .orElseThrow(
                                () ->
                                        new AccessDeniedException(
                                                "Authenticated identity is not provisioned"));

        Map<UUID, OrganizationMembershipBuilder> organizations = new LinkedHashMap<>();
        for (Membership membership :
                membershipRepository.findAllByUserProfileIdOrderByOrganizationDisplayName(
                        profile.getId())) {
            Organization organization = membership.getOrganization();
            OrganizationMembershipBuilder builder =
                    organizations.computeIfAbsent(
                            organization.getId(),
                            ignored ->
                                    new OrganizationMembershipBuilder(
                                            organization, membership.getRole()));
            if (membership.getWorkspace() == null) {
                workspaceRepository
                        .findAllByOrganizationIdOrderByDisplayName(organization.getId())
                        .forEach(builder::addWorkspace);
            } else {
                builder.addWorkspace(membership.getWorkspace());
            }
        }

        return new MeResponse(
                authenticatedUser.subject(),
                profile.getEmail(),
                profile.getDisplayName(),
                authenticatedUser.platformRoles().stream().sorted().toList(),
                organizations.values().stream().map(OrganizationMembershipBuilder::build).toList());
    }

    private static final class OrganizationMembershipBuilder {

        private final Organization organization;
        private final MembershipRole role;
        private final Map<UUID, WorkspaceResponse> workspaces = new LinkedHashMap<>();

        private OrganizationMembershipBuilder(Organization organization, MembershipRole role) {
            this.organization = organization;
            this.role = role;
        }

        private void addWorkspace(Workspace workspace) {
            workspaces.putIfAbsent(
                    workspace.getId(),
                    new WorkspaceResponse(
                            workspace.getId(), workspace.getSlug(), workspace.getDisplayName()));
        }

        private OrganizationMembershipResponse build() {
            List<WorkspaceResponse> sortedWorkspaces = new ArrayList<>(workspaces.values());
            sortedWorkspaces.sort(Comparator.comparing(WorkspaceResponse::displayName));
            return new OrganizationMembershipResponse(
                    organization.getId(),
                    organization.getSlug(),
                    organization.getDisplayName(),
                    role.name(),
                    List.copyOf(sortedWorkspaces));
        }
    }
}

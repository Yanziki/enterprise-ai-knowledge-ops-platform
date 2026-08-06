package io.github.yanziki.enterpriseai.tenant;

import io.github.yanziki.enterpriseai.identity.AuthenticatedUser;
import io.github.yanziki.enterpriseai.identity.AuthenticatedUserResolver;
import io.github.yanziki.enterpriseai.identity.MembershipRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantAuthorizationService {

    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;

    public TenantAuthorizationService(
            AuthenticatedUserResolver authenticatedUserResolver,
            OrganizationRepository organizationRepository,
            MembershipRepository membershipRepository) {
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public TenantContext requireAccess(
            JwtAuthenticationToken authentication, String organizationSlug) {
        AuthenticatedUser authenticatedUser = authenticatedUserResolver.resolve(authentication);
        Organization organization =
                organizationRepository
                        .findBySlug(organizationSlug)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Organization was not found"));

        boolean permitted =
                authenticatedUser.hasPlatformRole("PLATFORM_ADMIN")
                        || membershipRepository
                                .existsByUserProfileIdentitySubjectAndOrganizationIdAndWorkspaceIsNull(
                                        authenticatedUser.subject(), organization.getId());
        if (!permitted) {
            throw new AccessDeniedException("Organization access is denied");
        }

        return new TenantContext(
                authenticatedUser,
                organization.getId(),
                organization.getSlug(),
                organization.getDisplayName());
    }
}

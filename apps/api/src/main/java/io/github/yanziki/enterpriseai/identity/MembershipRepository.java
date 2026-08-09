package io.github.yanziki.enterpriseai.identity;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    @EntityGraph(attributePaths = {"organization", "workspace"})
    List<Membership> findAllByUserProfileIdOrderByOrganizationDisplayName(UUID userProfileId);

    boolean existsByUserProfileIdentitySubjectAndOrganizationIdAndWorkspaceIsNull(
            String identitySubject, UUID organizationId);

    @EntityGraph(attributePaths = {"organization", "workspace"})
    @Query(
            """
            SELECT membership
            FROM Membership membership
            WHERE membership.userProfile.identitySubject = :identitySubject
              AND membership.organization.id = :organizationId
              AND (membership.workspace IS NULL OR membership.workspace.id = :workspaceId)
            """)
    List<Membership> findApplicableWorkspaceMemberships(
            @Param("identitySubject") String identitySubject,
            @Param("organizationId") UUID organizationId,
            @Param("workspaceId") UUID workspaceId);
}

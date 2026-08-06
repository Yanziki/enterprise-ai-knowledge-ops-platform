package io.github.yanziki.enterpriseai.identity;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    @EntityGraph(attributePaths = {"organization", "workspace"})
    List<Membership> findAllByUserProfileIdOrderByOrganizationDisplayName(UUID userProfileId);

    boolean existsByUserProfileIdentitySubjectAndOrganizationIdAndWorkspaceIsNull(
            String identitySubject, UUID organizationId);
}

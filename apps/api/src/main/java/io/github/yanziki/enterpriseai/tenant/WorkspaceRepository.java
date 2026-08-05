package io.github.yanziki.enterpriseai.tenant;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    List<Workspace> findAllByOrganizationIdOrderByDisplayName(UUID organizationId);

    long countByOrganizationId(UUID organizationId);
}

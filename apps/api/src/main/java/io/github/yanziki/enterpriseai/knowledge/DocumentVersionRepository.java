package io.github.yanziki.enterpriseai.knowledge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

    List<DocumentVersion> findAllByDocumentIdOrderByVersionNumberDesc(UUID documentId);

    Optional<DocumentVersion> findFirstByDocumentIdOrderByVersionNumberDesc(UUID documentId);

    Optional<DocumentVersion> findByIdAndDocumentIdAndOrganizationIdAndWorkspaceId(
            UUID id, UUID documentId, UUID organizationId, UUID workspaceId);

    long countByDocumentId(UUID documentId);
}

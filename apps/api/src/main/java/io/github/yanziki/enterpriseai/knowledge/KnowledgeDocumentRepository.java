package io.github.yanziki.enterpriseai.knowledge;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {

    Optional<KnowledgeDocument> findByIdAndOrganizationIdAndWorkspaceId(
            UUID id, UUID organizationId, UUID workspaceId);

    Page<KnowledgeDocument> findAllByOrganizationIdAndWorkspaceIdAndStatus(
            UUID organizationId, UUID workspaceId, DocumentStatus status, Pageable pageable);
}

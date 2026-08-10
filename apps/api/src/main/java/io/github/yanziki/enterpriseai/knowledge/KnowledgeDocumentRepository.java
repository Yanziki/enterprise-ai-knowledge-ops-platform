package io.github.yanziki.enterpriseai.knowledge;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {

    Optional<KnowledgeDocument> findByIdAndOrganizationIdAndWorkspaceId(
            UUID id, UUID organizationId, UUID workspaceId);

    Page<KnowledgeDocument> findAllByOrganizationIdAndWorkspaceIdAndStatus(
            UUID organizationId, UUID workspaceId, DocumentStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT document
            FROM KnowledgeDocument document
            WHERE document.id = :id
              AND document.organizationId = :organizationId
              AND document.workspaceId = :workspaceId
            """)
    Optional<KnowledgeDocument> findScopedForUpdate(
            @Param("id") UUID id,
            @Param("organizationId") UUID organizationId,
            @Param("workspaceId") UUID workspaceId);
}

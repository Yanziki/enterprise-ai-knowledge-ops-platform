package io.github.yanziki.enterpriseai.knowledge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

    List<DocumentVersion> findAllByDocumentIdOrderByVersionNumberDesc(UUID documentId);

    Optional<DocumentVersion> findFirstByDocumentIdOrderByVersionNumberDesc(UUID documentId);

    Optional<DocumentVersion> findByIdAndDocumentIdAndOrganizationIdAndWorkspaceId(
            UUID id, UUID documentId, UUID organizationId, UUID workspaceId);

    @Query(
            value =
                    """
                    SELECT EXISTS (
                        SELECT 1
                        FROM document_versions document_version
                        LEFT JOIN document_ingestion_jobs job
                          ON job.document_version_id = document_version.id
                        WHERE document_version.document_id = :documentId
                          AND (
                              document_version.ingestion_status IN ('STORED', 'QUEUED', 'PROCESSING')
                              OR job.status IN ('QUEUED', 'PROCESSING')
                          )
                    )
                    """,
            nativeQuery = true)
    boolean existsInFlightIngestionByDocumentId(@Param("documentId") UUID documentId);

    long countByDocumentId(UUID documentId);
}

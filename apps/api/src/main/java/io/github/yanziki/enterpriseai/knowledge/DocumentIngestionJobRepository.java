package io.github.yanziki.enterpriseai.knowledge;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentIngestionJobRepository extends JpaRepository<DocumentIngestionJob, UUID> {

    Optional<DocumentIngestionJob> findByDocumentVersionId(UUID documentVersionId);
}

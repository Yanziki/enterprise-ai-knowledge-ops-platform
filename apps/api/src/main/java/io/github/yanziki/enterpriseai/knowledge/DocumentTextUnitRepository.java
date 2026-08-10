package io.github.yanziki.enterpriseai.knowledge;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentTextUnitRepository extends JpaRepository<DocumentTextUnit, UUID> {

    List<DocumentTextUnit> findAllByDocumentVersionIdOrderByOrdinal(UUID documentVersionId);

    long countByDocumentVersionId(UUID documentVersionId);

    void deleteAllByDocumentVersionId(UUID documentVersionId);
}

package io.github.yanziki.enterpriseai.answer;

import io.github.yanziki.enterpriseai.tenant.WorkspaceAccessContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AnswerAttemptStore {

    private final JdbcTemplate jdbcTemplate;

    public AnswerAttemptStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void persist(
            WorkspaceAccessContext scope,
            String question,
            AnswerResponse response,
            AssembledContext context) {
        Instant createdAt = Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO answer_attempts (
                    id, organization_id, workspace_id, created_by_subject, question,
                    status, answer_text, requested_retrieval_mode, effective_retrieval_mode,
                    retrieved_chunk_count, context_characters, provider_id, model_id, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                response.requestId(),
                scope.organizationId(),
                scope.workspaceId(),
                scope.authenticatedUser().subject(),
                question,
                response.status().name(),
                response.answer(),
                response.requestedRetrievalMode().name(),
                response.effectiveRetrievalMode().name(),
                response.retrievedChunkCount(),
                response.contextCharacters(),
                response.provider(),
                response.model(),
                Timestamp.from(createdAt));

        Set<String> citedAliases =
                response.citations().stream()
                        .map(AnswerCitation::citationId)
                        .collect(Collectors.toUnmodifiableSet());
        for (GroundedEvidence evidence : context.evidence()) {
            var source = evidence.source();
            var citation = source.citation();
            jdbcTemplate.update(
                    """
                    INSERT INTO answer_attempt_evidence (
                        id, answer_attempt_id, organization_id, workspace_id,
                        citation_alias, rank, cited, retrieval_chunk_id, document_id,
                        document_version_id, version_number, document_title, locator_type,
                        locator_value, start_character, end_character, excerpt, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    response.requestId(),
                    scope.organizationId(),
                    scope.workspaceId(),
                    evidence.citationId(),
                    source.rank(),
                    citedAliases.contains(evidence.citationId()),
                    source.chunkId(),
                    citation.documentId(),
                    citation.documentVersionId(),
                    citation.versionNumber(),
                    citation.documentTitle(),
                    citation.locatorType(),
                    citation.locatorValue(),
                    citation.startCharacter(),
                    citation.endCharacter(),
                    evidence.content(),
                    Timestamp.from(createdAt));
        }
    }
}

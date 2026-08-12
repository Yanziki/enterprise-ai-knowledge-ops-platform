package io.github.yanziki.enterpriseai.retrieval.search;

import io.github.yanziki.enterpriseai.knowledge.api.KnowledgeApiException;
import io.github.yanziki.enterpriseai.retrieval.RetrievalMode;
import io.github.yanziki.enterpriseai.retrieval.RetrievalProperties;
import io.github.yanziki.enterpriseai.retrieval.embedding.EmbeddingProvider;
import io.github.yanziki.enterpriseai.retrieval.embedding.EmbeddingProviderRegistry;
import io.github.yanziki.enterpriseai.retrieval.embedding.EmbeddingValidation;
import io.github.yanziki.enterpriseai.tenant.WorkspaceAccessContext;
import io.github.yanziki.enterpriseai.tenant.WorkspaceAuthorizationService;
import io.github.yanziki.enterpriseai.tenant.WorkspaceOperation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class RetrievalSearchService {

    private static final int RRF_K = 60;
    private static final int MAX_SNIPPET_CHARACTERS = 600;
    private final WorkspaceAuthorizationService authorizationService;
    private final RetrievalSearchRepository repository;
    private final EmbeddingProviderRegistry providerRegistry;
    private final RetrievalProperties properties;

    public RetrievalSearchService(
            WorkspaceAuthorizationService authorizationService,
            RetrievalSearchRepository repository,
            EmbeddingProviderRegistry providerRegistry,
            RetrievalProperties properties) {
        this.authorizationService = authorizationService;
        this.repository = repository;
        this.providerRegistry = providerRegistry;
        this.properties = properties;
    }

    public RetrievalSearchResponse search(
            JwtAuthenticationToken authentication,
            String organizationSlug,
            String workspaceSlug,
            RetrievalSearchRequest request) {
        WorkspaceAccessContext scope =
                authorizationService.requireAccess(
                        authentication,
                        organizationSlug,
                        workspaceSlug,
                        WorkspaceOperation.SEARCH_CONTENT);
        String query = validQuery(request == null ? null : request.query());
        RetrievalMode requestedMode =
                request == null || request.mode() == null ? RetrievalMode.AUTO : request.mode();
        int topK = validTopK(request == null ? null : request.topK());
        Optional<EmbeddingProvider> provider = compatibleProvider(scope);
        RetrievalMode effectiveMode = resolveMode(requestedMode, provider.isPresent());

        List<RetrievalCandidate> lexical = List.of();
        List<RetrievalCandidate> vector = List.of();
        if (effectiveMode == RetrievalMode.LEXICAL || effectiveMode == RetrievalMode.HYBRID) {
            lexical =
                    repository.lexical(
                            scope.organizationId(),
                            scope.workspaceId(),
                            query,
                            properties.candidateLimit());
        }
        if (effectiveMode == RetrievalMode.VECTOR || effectiveMode == RetrievalMode.HYBRID) {
            EmbeddingProvider activeProvider = provider.orElseThrow();
            float[] queryVector = activeProvider.embedQuery(query);
            EmbeddingValidation.validate(queryVector, activeProvider.dimension());
            vector =
                    repository.vector(
                            scope.organizationId(),
                            scope.workspaceId(),
                            queryVector,
                            activeProvider,
                            properties.candidateLimit());
        }
        List<RetrievalSearchResult> results = rank(lexical, vector, effectiveMode, topK);
        return new RetrievalSearchResponse(
                query, requestedMode, effectiveMode, topK, List.copyOf(results));
    }

    public RetrievalCapabilitiesResponse capabilities(
            JwtAuthenticationToken authentication, String organizationSlug, String workspaceSlug) {
        WorkspaceAccessContext scope =
                authorizationService.requireAccess(
                        authentication,
                        organizationSlug,
                        workspaceSlug,
                        WorkspaceOperation.SEARCH_CONTENT);
        Optional<EmbeddingProvider> provider = compatibleProvider(scope);
        List<RetrievalMode> modes =
                new ArrayList<>(List.of(RetrievalMode.AUTO, RetrievalMode.LEXICAL));
        provider.ifPresent(
                ignored -> {
                    modes.add(RetrievalMode.VECTOR);
                    modes.add(RetrievalMode.HYBRID);
                });
        return new RetrievalCapabilitiesResponse(
                true,
                provider.isPresent(),
                List.copyOf(modes),
                provider.isPresent() ? RetrievalMode.HYBRID : RetrievalMode.LEXICAL,
                provider.map(EmbeddingProvider::providerId).orElse(null),
                provider.map(EmbeddingProvider::modelId).orElse(null),
                provider.map(EmbeddingProvider::dimension).orElse(null));
    }

    private Optional<EmbeddingProvider> compatibleProvider(WorkspaceAccessContext scope) {
        return providerRegistry
                .activeProvider()
                .filter(
                        provider ->
                                repository.hasCompatibleVectors(
                                        scope.organizationId(), scope.workspaceId(), provider));
    }

    private RetrievalMode resolveMode(RetrievalMode requested, boolean vectorAvailable) {
        if (requested == RetrievalMode.AUTO) {
            return vectorAvailable ? RetrievalMode.HYBRID : RetrievalMode.LEXICAL;
        }
        if (EnumSet.of(RetrievalMode.VECTOR, RetrievalMode.HYBRID).contains(requested)
                && !vectorAvailable) {
            throw new KnowledgeApiException(
                    HttpStatus.CONFLICT,
                    "RETRIEVAL_MODE_UNAVAILABLE",
                    "The requested retrieval mode is not available for this workspace");
        }
        return requested;
    }

    private List<RetrievalSearchResult> rank(
            List<RetrievalCandidate> lexical,
            List<RetrievalCandidate> vector,
            RetrievalMode mode,
            int topK) {
        Map<UUID, MutableRank> ranks = new HashMap<>();
        addRanking(ranks, lexical, true);
        addRanking(ranks, vector, false);
        Comparator<MutableRank> order;
        if (mode == RetrievalMode.LEXICAL) {
            order = Comparator.comparing(MutableRank::lexicalScore, nullsLastReverse());
        } else if (mode == RetrievalMode.VECTOR) {
            order = Comparator.comparing(MutableRank::vectorScore, nullsLastReverse());
        } else {
            order = Comparator.comparingDouble(MutableRank::rrfScore).reversed();
        }
        List<MutableRank> ordered =
                ranks.values().stream()
                        .sorted(order.thenComparing(rank -> rank.candidate.chunkId()))
                        .limit(topK)
                        .toList();
        List<RetrievalSearchResult> results = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            MutableRank rank = ordered.get(index);
            RetrievalCandidate candidate = rank.candidate;
            double score =
                    mode == RetrievalMode.LEXICAL
                            ? rank.lexicalScore
                            : mode == RetrievalMode.VECTOR ? rank.vectorScore : rank.rrfScore;
            results.add(
                    new RetrievalSearchResult(
                            candidate.chunkId(),
                            index + 1,
                            score,
                            rank.lexicalScore,
                            rank.vectorScore,
                            citation(candidate)));
        }
        return results;
    }

    private void addRanking(
            Map<UUID, MutableRank> ranks, List<RetrievalCandidate> candidates, boolean lexical) {
        for (int index = 0; index < candidates.size(); index++) {
            RetrievalCandidate candidate = candidates.get(index);
            MutableRank rank =
                    ranks.computeIfAbsent(
                            candidate.chunkId(), ignored -> new MutableRank(candidate));
            rank.rrfScore += 1.0 / (RRF_K + index + 1.0);
            if (lexical) {
                rank.lexicalScore = candidate.componentScore();
            } else {
                rank.vectorScore = candidate.componentScore();
            }
        }
    }

    private RetrievalCitation citation(RetrievalCandidate candidate) {
        String snippet = candidate.text();
        if (snippet.length() > MAX_SNIPPET_CHARACTERS) {
            snippet = snippet.substring(0, MAX_SNIPPET_CHARACTERS).stripTrailing() + "…";
        }
        return new RetrievalCitation(
                candidate.documentId(),
                candidate.documentTitle(),
                candidate.documentVersionId(),
                candidate.versionNumber(),
                candidate.locatorType(),
                candidate.locatorValue(),
                candidate.startCharacter(),
                candidate.endCharacter(),
                snippet);
    }

    private String validQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new KnowledgeApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_QUERY",
                    "A non-empty search query is required");
        }
        String normalized = query.strip();
        if (normalized.length() > properties.maxQueryCharacters()) {
            throw new KnowledgeApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_QUERY",
                    "The search query exceeds the configured length limit");
        }
        return normalized;
    }

    private int validTopK(Integer requested) {
        int topK = requested == null ? 5 : requested;
        if (topK < 1 || topK > properties.maxTopK()) {
            throw new KnowledgeApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_TOP_K",
                    "The requested result count is outside the supported range");
        }
        return topK;
    }

    private Comparator<Double> nullsLastReverse() {
        return Comparator.nullsLast(Comparator.reverseOrder());
    }

    private static final class MutableRank {
        private final RetrievalCandidate candidate;
        private double rrfScore;
        private Double lexicalScore;
        private Double vectorScore;

        private MutableRank(RetrievalCandidate candidate) {
            this.candidate = candidate;
        }

        private double rrfScore() {
            return rrfScore;
        }

        private Double lexicalScore() {
            return lexicalScore;
        }

        private Double vectorScore() {
            return vectorScore;
        }
    }
}

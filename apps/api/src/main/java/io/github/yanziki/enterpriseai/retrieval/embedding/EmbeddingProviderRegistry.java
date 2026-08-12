package io.github.yanziki.enterpriseai.retrieval.embedding;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingProviderRegistry {

    private final Optional<EmbeddingProvider> provider;

    public EmbeddingProviderRegistry(List<EmbeddingProvider> providers) {
        if (providers.size() > 1) {
            throw new IllegalStateException("Only one embedding provider may be active");
        }
        provider = providers.stream().findFirst();
    }

    public Optional<EmbeddingProvider> activeProvider() {
        return provider;
    }
}

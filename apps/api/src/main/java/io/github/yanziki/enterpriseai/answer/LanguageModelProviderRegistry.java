package io.github.yanziki.enterpriseai.answer;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class LanguageModelProviderRegistry {

    private final Optional<LanguageModelProvider> provider;

    public LanguageModelProviderRegistry(List<LanguageModelProvider> providers) {
        if (providers.size() > 1) {
            throw new IllegalStateException("Only one language model provider may be active");
        }
        provider = providers.stream().findFirst();
    }

    public Optional<LanguageModelProvider> activeProvider() {
        return provider;
    }
}

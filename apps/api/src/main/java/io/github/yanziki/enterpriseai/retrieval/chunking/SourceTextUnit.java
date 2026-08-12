package io.github.yanziki.enterpriseai.retrieval.chunking;

import java.util.UUID;

public record SourceTextUnit(
        UUID id, int ordinal, String locatorType, String locatorValue, String text) {}

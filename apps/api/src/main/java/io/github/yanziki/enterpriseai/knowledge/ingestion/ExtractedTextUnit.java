package io.github.yanziki.enterpriseai.knowledge.ingestion;

import io.github.yanziki.enterpriseai.knowledge.TextLocatorType;

public record ExtractedTextUnit(
        int ordinal, TextLocatorType locatorType, String locatorValue, String text) {}

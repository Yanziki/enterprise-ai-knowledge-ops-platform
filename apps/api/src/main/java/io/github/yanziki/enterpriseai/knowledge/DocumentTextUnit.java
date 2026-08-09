package io.github.yanziki.enterpriseai.knowledge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_text_units")
public class DocumentTextUnit {

    @Id private UUID id;

    @Column(name = "document_version_id", nullable = false)
    private UUID documentVersionId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private int ordinal;

    @Enumerated(EnumType.STRING)
    @Column(name = "locator_type", nullable = false, length = 32)
    private TextLocatorType locatorType;

    @Column(name = "locator_value", nullable = false, length = 120)
    private String locatorValue;

    @Column(name = "text_content", nullable = false, columnDefinition = "text")
    private String textContent;

    @Column(name = "character_count", nullable = false)
    private int characterCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DocumentTextUnit() {}

    public DocumentTextUnit(
            UUID id,
            UUID documentVersionId,
            UUID organizationId,
            UUID workspaceId,
            int ordinal,
            TextLocatorType locatorType,
            String locatorValue,
            String textContent,
            Instant createdAt) {
        this.id = id;
        this.documentVersionId = documentVersionId;
        this.organizationId = organizationId;
        this.workspaceId = workspaceId;
        this.ordinal = ordinal;
        this.locatorType = locatorType;
        this.locatorValue = locatorValue;
        this.textContent = textContent;
        this.characterCount = textContent.length();
        this.createdAt = createdAt;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public TextLocatorType getLocatorType() {
        return locatorType;
    }

    public String getLocatorValue() {
        return locatorValue;
    }

    public String getTextContent() {
        return textContent;
    }

    public int getCharacterCount() {
        return characterCount;
    }
}

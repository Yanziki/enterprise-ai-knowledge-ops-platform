package io.github.yanziki.enterpriseai.knowledge.storage;

import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DocumentObjectKeyFactory {

    public String create(
            UUID organizationId, UUID workspaceId, UUID documentId, UUID documentVersionId) {
        return "organizations/%s/workspaces/%s/documents/%s/versions/%s/original"
                .formatted(
                        Objects.requireNonNull(organizationId, "organizationId"),
                        Objects.requireNonNull(workspaceId, "workspaceId"),
                        Objects.requireNonNull(documentId, "documentId"),
                        Objects.requireNonNull(documentVersionId, "documentVersionId"));
    }
}

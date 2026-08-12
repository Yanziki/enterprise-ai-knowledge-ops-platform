package io.github.yanziki.enterpriseai.tenant;

import java.util.EnumSet;
import java.util.Set;

public enum WorkspaceAccessRole {
    PLATFORM_ADMIN(4, EnumSet.allOf(WorkspaceOperation.class)),
    TENANT_ADMIN(3, EnumSet.allOf(WorkspaceOperation.class)),
    MEMBER(
            2,
            EnumSet.of(
                    WorkspaceOperation.READ_METADATA,
                    WorkspaceOperation.DOWNLOAD_ORIGINAL,
                    WorkspaceOperation.SEARCH_CONTENT)),
    AUDITOR(1, EnumSet.of(WorkspaceOperation.READ_METADATA));

    private final int privilege;
    private final Set<WorkspaceOperation> operations;

    WorkspaceAccessRole(int privilege, Set<WorkspaceOperation> operations) {
        this.privilege = privilege;
        this.operations = Set.copyOf(operations);
    }

    public boolean permits(WorkspaceOperation operation) {
        return operations.contains(operation);
    }

    public int privilege() {
        return privilege;
    }
}

package com.cloudsql.lab.admin.dto;

import com.cloudsql.lab.database.model.DatabaseWorkspace;
import java.time.OffsetDateTime;

public record AdminDatabaseResponse(
        String id,
        String name,
        String ownerUserId,
        String status,
        OffsetDateTime createdAt,
        long storageUsedBytes,
        long storageLimitBytes) {

    public static AdminDatabaseResponse from(DatabaseWorkspace workspace, long storageUsedBytes) {
        return new AdminDatabaseResponse(workspace.id(), workspace.name(), workspace.ownerUserId(), workspace.status(),
                workspace.createdAt(), storageUsedBytes, workspace.storageLimitBytes());
    }
}

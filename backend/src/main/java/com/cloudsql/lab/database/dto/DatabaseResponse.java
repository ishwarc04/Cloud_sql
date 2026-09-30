package com.cloudsql.lab.database.dto;

import com.cloudsql.lab.database.model.DatabaseWorkspace;
import java.time.OffsetDateTime;

public record DatabaseResponse(
        String id,
        String name,
        String status,
        OffsetDateTime createdAt,
        long storageUsedBytes,
        long storageLimitBytes) {

    public static DatabaseResponse from(DatabaseWorkspace workspace) {
        return new DatabaseResponse(workspace.id(), workspace.name(), workspace.status(), workspace.createdAt(),
                workspace.storageUsedBytes(), workspace.storageLimitBytes());
    }
}

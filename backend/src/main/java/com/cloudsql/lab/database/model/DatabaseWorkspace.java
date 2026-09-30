package com.cloudsql.lab.database.model;

import java.time.OffsetDateTime;

public record DatabaseWorkspace(
        String id,
        String internalWorkspaceId,
        String name,
        String ownerUserId,
        String status,
        OffsetDateTime createdAt,
        long storageUsedBytes,
        long storageLimitBytes) {

    public DatabaseWorkspace withStorageUsedBytes(long bytes) {
        return new DatabaseWorkspace(id, internalWorkspaceId, name, ownerUserId, status, createdAt, bytes, storageLimitBytes);
    }
}

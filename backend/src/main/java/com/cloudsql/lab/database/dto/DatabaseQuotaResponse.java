package com.cloudsql.lab.database.dto;

public record DatabaseQuotaResponse(
        int databaseCount,
        int maximumDatabases,
        long totalStorageUsedBytes,
        long totalStorageLimitBytes) {
}

package com.cloudsql.lab.database;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloudsql.workspaces")
public record WorkspaceProperties(
        int maxDatabasesPerUser,
        long storageLimitBytes,
        int queryTimeoutSeconds,
        int maxReturnedRows,
        String storageDirectory) {
}

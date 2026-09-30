package com.cloudsql.lab.admin.dto;

import java.time.OffsetDateTime;

public record AdminOverviewResponse(
        long totalDatabaseWorkspaces,
        long activeDatabaseWorkspaces,
        long totalStorageUsedBytes,
        long totalStorageCapacityBytes,
        long availableStorageBytes,
        long totalPracticeProblems,
        long solvedProblemCount,
        long totalPracticeAttempts,
        String serviceStatus,
        OffsetDateTime generatedAt) {
}

package com.cloudsql.lab.progress;

import java.time.Instant;

public record ProblemProgress(String userId, long problemId, String status, int attempts, Instant solvedAt) {
    public static ProblemProgress notStarted(String userId, long problemId) {
        return new ProblemProgress(userId, problemId, "NOT_STARTED", 0, null);
    }
}

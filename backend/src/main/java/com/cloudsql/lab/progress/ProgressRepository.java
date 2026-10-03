package com.cloudsql.lab.progress;

import java.util.Map;

public interface ProgressRepository {
    ProgressStats summarize();
    Map<Long, ProblemProgress> findByUserId(String userId);
    ProblemProgress find(String userId, long problemId);
    RecordedAttempt recordAttempt(String userId, long problemId, boolean solved, int points, String query, String outcome);
    record RecordedAttempt(ProblemProgress progress, int pointsAwarded) { }
}

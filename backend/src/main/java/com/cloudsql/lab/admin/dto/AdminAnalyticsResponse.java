package com.cloudsql.lab.admin.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AdminAnalyticsResponse(Summary summary, List<DailyActivity> dailyActivity, List<User> users,
        List<Workspace> workspaces, List<Problem> problems, List<Submission> recentSubmissions,
        Quotas quotas, Instant generatedAt) {
    public record Summary(long totalUsers, long learners, long administrators, long activeUsersSevenDays,
            long activeSessions, long totalSubmissions, long acceptedSubmissions, long wrongAnswers, long queryErrors,
            long solvedUserProblems, long totalPoints, long totalWorkspaces, long storageUsedBytes,
            long storageAllocatedBytes, long databaseLatencyMs) { }
    public record DailyActivity(LocalDate date, long submissions, long accepted, long errors, long registrations) { }
    public record User(String id, String name, String email, String role, Instant createdAt, long solvedProblems,
            long attempts, long points, long databaseCount, long storageUsedBytes, long activeSessions, Instant lastSubmissionAt) { }
    public record Workspace(String id, String name, String ownerUserId, String ownerName, String ownerEmail,
            String status, Instant createdAt, long storageUsedBytes, long storageLimitBytes) { }
    public record Problem(long id, String title, String difficulty, String category, long attempts, long accepted,
            long errors, long solvedUsers) { }
    public record Submission(String id, String userId, String userName, long problemId, String problemTitle,
            String difficulty, String category, String outcome, int pointsAwarded, Instant submittedAt) { }
    public record Quotas(int maxDatabasesPerUser, long storageLimitPerDatabaseBytes, int queryTimeoutSeconds, int maxReturnedRows) { }
}

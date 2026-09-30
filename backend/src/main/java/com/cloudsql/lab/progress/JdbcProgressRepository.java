package com.cloudsql.lab.progress;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcProgressRepository implements ProgressRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcProgressRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ProgressStats summarize() {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(CASE WHEN status = 'SOLVED' THEN 1 END) AS solved_problems,
                       COALESCE(SUM(attempts), 0) AS total_attempts
                FROM platform.user_problem_progress
                """, (resultSet, rowNumber) -> new ProgressStats(
                        resultSet.getLong("solved_problems"), resultSet.getLong("total_attempts")));
    }

    @Override
    public Map<Long, ProblemProgress> findByUserId(String userId) {
        return jdbcTemplate.query("SELECT user_id, problem_id, status, attempts, solved_at FROM platform.user_problem_progress WHERE user_id = ?",
                (resultSet, rowNumber) -> mapProgress(resultSet.getString("user_id"), resultSet.getLong("problem_id"), resultSet.getString("status"), resultSet.getInt("attempts"), resultSet.getTimestamp("solved_at")), userId)
                .stream().collect(Collectors.toMap(ProblemProgress::problemId, progress -> progress));
    }

    @Override
    public ProblemProgress find(String userId, long problemId) {
        return jdbcTemplate.query("SELECT user_id, problem_id, status, attempts, solved_at FROM platform.user_problem_progress WHERE user_id = ? AND problem_id = ?",
                (resultSet, rowNumber) -> mapProgress(resultSet.getString("user_id"), resultSet.getLong("problem_id"), resultSet.getString("status"), resultSet.getInt("attempts"), resultSet.getTimestamp("solved_at")), userId, problemId)
                .stream().findFirst().orElse(ProblemProgress.notStarted(userId, problemId));
    }

    @Override
    @Transactional
    public ProblemProgress recordAttempt(String userId, long problemId, boolean solved) {
        Instant now = Instant.now();
        int updated = jdbcTemplate.update("UPDATE platform.user_problem_progress SET attempts = attempts + 1, status = CASE WHEN ? THEN 'SOLVED' ELSE status END, solved_at = CASE WHEN ? AND solved_at IS NULL THEN ? ELSE solved_at END WHERE user_id = ? AND problem_id = ?",
                solved, solved, Timestamp.from(now), userId, problemId);
        if (updated == 0) {
            jdbcTemplate.update("INSERT INTO platform.user_problem_progress (user_id, problem_id, status, attempts, solved_at) VALUES (?, ?, ?, 1, ?)",
                    userId, problemId, solved ? "SOLVED" : "ATTEMPTED", solved ? Timestamp.from(now) : null);
        }
        return find(userId, problemId);
    }

    private ProblemProgress mapProgress(String userId, long problemId, String status, int attempts, Timestamp solvedAt) {
        return new ProblemProgress(userId, problemId, status, attempts, solvedAt == null ? null : solvedAt.toInstant());
    }
}

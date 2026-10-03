package com.cloudsql.lab.admin;

import com.cloudsql.lab.admin.dto.AdminAnalyticsResponse;
import com.cloudsql.lab.admin.dto.AdminAnalyticsResponse.*;
import com.cloudsql.lab.database.WorkspaceProperties;
import com.cloudsql.lab.problem.ProblemRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAnalyticsService {
    private final JdbcTemplate jdbc;
    private final ProblemRepository problems;
    private final WorkspaceProperties quotas;
    public AdminAnalyticsService(JdbcTemplate jdbc, ProblemRepository problems, WorkspaceProperties quotas) {
        this.jdbc = jdbc; this.problems = problems; this.quotas = quotas;
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminAnalyticsResponse snapshot() {
        Instant now = Instant.now();
        Timestamp current = Timestamp.from(now);
        long started = System.nanoTime();
        jdbc.queryForObject("SELECT 1", Integer.class);
        long latency = Math.max(1, (System.nanoTime() - started) / 1_000_000);
        long[] accounts = jdbc.queryForObject("SELECT COUNT(*), COUNT(CASE WHEN role = 'USER' THEN 1 END), COUNT(CASE WHEN role = 'ADMIN' THEN 1 END) FROM platform.users", (rs, row) -> values(rs, 3));
        long[] submissions = jdbc.queryForObject("SELECT COUNT(*), COUNT(CASE WHEN outcome = 'ACCEPTED' THEN 1 END), COUNT(CASE WHEN outcome = 'WRONG_ANSWER' THEN 1 END), COUNT(CASE WHEN outcome = 'ERROR' THEN 1 END) FROM platform.submissions", (rs, row) -> values(rs, 4));
        // Join real accounts to exclude unassigned demo progress from account metrics.
        long[] progress = jdbc.queryForObject("SELECT COUNT(CASE WHEN p.status = 'SOLVED' THEN 1 END), COALESCE(SUM(p.points), 0) FROM platform.user_problem_progress p JOIN platform.users u ON u.id = p.user_id", (rs, row) -> values(rs, 2));
        long[] resources = jdbc.queryForObject("SELECT COUNT(*), COALESCE(SUM(storage_used_bytes), 0), COALESCE(SUM(storage_limit_bytes), 0) FROM platform.database_workspaces", (rs, row) -> values(rs, 3));
        long active = jdbc.queryForObject("SELECT COUNT(DISTINCT user_id) FROM platform.submissions WHERE submitted_at >= ?", Long.class, Timestamp.from(now.minusSeconds(7 * 86400)));
        long sessions = jdbc.queryForObject("SELECT COUNT(*) FROM platform.sessions WHERE expires_at > ?", Long.class, current);
        Summary summary = new Summary(accounts[0], accounts[1], accounts[2], active, sessions, submissions[0], submissions[1], submissions[2], submissions[3], progress[0], progress[1], resources[0], resources[1], resources[2], latency);
        return new AdminAnalyticsResponse(summary, activity(now), users(current), workspaces(), problemStats(), recent(null),
                new Quotas(quotas.maxDatabasesPerUser(), quotas.storageLimitBytes(), quotas.queryTimeoutSeconds(), quotas.maxReturnedRows()), now);
    }
    public record UserDetail(User user, List<Workspace> workspaces, List<Submission> recentSubmissions) { }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public UserDetail user(String id) {
        User account = jdbc.query(USER_SQL + " WHERE u.id = ?", this::mapUser, Timestamp.from(Instant.now()), id).stream().findFirst()
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Account not found."));
        return new UserDetail(account, jdbc.query(WORKSPACE_SQL + " WHERE w.owner_user_id = ? ORDER BY w.created_at DESC", this::mapWorkspace, id), recent(id));
    }
    private static final String USER_SQL = """
        SELECT u.id, u.name, u.email, u.role, u.created_at,
          COALESCE(p.solved, 0) AS solved, COALESCE(p.attempts, 0) AS attempts, COALESCE(p.points, 0) AS points,
          COALESCE(w.databases, 0) AS databases, COALESCE(w.storage, 0) AS storage,
          COALESCE(s.sessions, 0) AS sessions, a.last_submission
        FROM platform.users u
        LEFT JOIN (SELECT user_id, COUNT(CASE WHEN status = 'SOLVED' THEN 1 END) AS solved, SUM(attempts) AS attempts, SUM(points) AS points FROM platform.user_problem_progress GROUP BY user_id) p ON p.user_id = u.id
        LEFT JOIN (SELECT owner_user_id, COUNT(*) AS databases, SUM(storage_used_bytes) AS storage FROM platform.database_workspaces GROUP BY owner_user_id) w ON w.owner_user_id = u.id
        LEFT JOIN (SELECT user_id, COUNT(*) AS sessions FROM platform.sessions WHERE expires_at > ? GROUP BY user_id) s ON s.user_id = u.id
        LEFT JOIN (SELECT user_id, MAX(submitted_at) AS last_submission FROM platform.submissions GROUP BY user_id) a ON a.user_id = u.id
        """;
    private List<User> users(Timestamp now) {
        return jdbc.query(USER_SQL + " ORDER BY u.created_at DESC, u.id LIMIT 200", this::mapUser, now);
    }
    private User mapUser(ResultSet rs, int row) throws SQLException {
        return new User(rs.getString("id"), rs.getString("name"), rs.getString("email"), rs.getString("role"), instant(rs, "created_at"), rs.getLong("solved"), rs.getLong("attempts"), rs.getLong("points"), rs.getLong("databases"), rs.getLong("storage"), rs.getLong("sessions"), instant(rs, "last_submission"));
    }
    private static final String WORKSPACE_SQL = """
        SELECT w.id, w.name, w.owner_user_id, COALESCE(u.name, 'Unassigned demo account') AS owner_name,
          u.email AS owner_email, w.status, w.created_at, w.storage_used_bytes, w.storage_limit_bytes
        FROM platform.database_workspaces w LEFT JOIN platform.users u ON u.id = w.owner_user_id
        """;
    private List<Workspace> workspaces() { return jdbc.query(WORKSPACE_SQL + " ORDER BY w.created_at DESC, w.id LIMIT 200", this::mapWorkspace); }
    private Workspace mapWorkspace(ResultSet rs, int row) throws SQLException {
        return new Workspace(rs.getString("id"), rs.getString("name"), rs.getString("owner_user_id"), rs.getString("owner_name"), rs.getString("owner_email"), rs.getString("status"), instant(rs, "created_at"), rs.getLong("storage_used_bytes"), rs.getLong("storage_limit_bytes"));
    }
    private List<DailyActivity> activity(Instant now) {
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        Timestamp since = Timestamp.from(today.minusDays(13).atStartOfDay(ZoneOffset.UTC).toInstant());
        Map<LocalDate, long[]> submissions = new HashMap<>();
        jdbc.query("SELECT CAST(submitted_at AS DATE), COUNT(*), COUNT(CASE WHEN outcome = 'ACCEPTED' THEN 1 END), COUNT(CASE WHEN outcome = 'ERROR' THEN 1 END) FROM platform.submissions WHERE submitted_at >= ? GROUP BY CAST(submitted_at AS DATE)", rs -> {
            submissions.put(rs.getDate(1).toLocalDate(), new long[]{rs.getLong(2), rs.getLong(3), rs.getLong(4)});
        }, since);
        Map<LocalDate, Long> registrations = new HashMap<>();
        jdbc.query("SELECT CAST(created_at AS DATE), COUNT(*) FROM platform.users WHERE created_at >= ? GROUP BY CAST(created_at AS DATE)", rs -> {
            registrations.put(rs.getDate(1).toLocalDate(), rs.getLong(2));
        }, since);
        return java.util.stream.IntStream.range(0, 14).mapToObj(i -> {
            LocalDate date = today.minusDays(13 - i);
            long[] counts = submissions.getOrDefault(date, new long[3]);
            return new DailyActivity(date, counts[0], counts[1], counts[2], registrations.getOrDefault(date, 0L));
        }).toList();
    }
    private List<Problem> problemStats() {
        Map<Long, long[]> submitted = new HashMap<>();
        jdbc.query("SELECT problem_id, COUNT(*), COUNT(CASE WHEN outcome = 'ACCEPTED' THEN 1 END), COUNT(CASE WHEN outcome = 'ERROR' THEN 1 END) FROM platform.submissions GROUP BY problem_id", rs -> {
            submitted.put(rs.getLong(1), new long[]{rs.getLong(2), rs.getLong(3), rs.getLong(4)});
        });
        Map<Long, Long> solved = new HashMap<>();
        jdbc.query("SELECT p.problem_id, COUNT(*) FROM platform.user_problem_progress p JOIN platform.users u ON u.id = p.user_id WHERE p.status = 'SOLVED' GROUP BY p.problem_id", rs -> {
            solved.put(rs.getLong(1), rs.getLong(2));
        });
        return problems.findAll().stream().map(p -> {
            long[] counts = submitted.getOrDefault(p.id(), new long[3]);
            return new Problem(p.id(), p.title(), p.difficulty(), p.topic(), counts[0], counts[1], counts[2], solved.getOrDefault(p.id(), 0L));
        }).toList();
    }
    private List<Submission> recent(String userId) {
        String sql = "SELECT s.id, s.user_id, u.name, s.problem_id, s.outcome, s.points_awarded, s.submitted_at FROM platform.submissions s JOIN platform.users u ON u.id = s.user_id";
        if (userId != null) sql += " WHERE s.user_id = ?";
        sql += " ORDER BY s.submitted_at DESC, s.id DESC LIMIT 50";
        return jdbc.query(sql, (rs, row) -> {
            var problem = problems.findById(rs.getLong("problem_id")).orElseThrow();
            return new Submission(rs.getString("id"), rs.getString("user_id"), rs.getString("name"), problem.id(), problem.title(), problem.difficulty(), problem.topic(), rs.getString("outcome"), rs.getInt("points_awarded"), instant(rs, "submitted_at"));
        }, userId == null ? new Object[0] : new Object[]{userId});
    }
    private static long[] values(ResultSet rs, int size) throws SQLException {
        long[] values = new long[size];
        for (int i = 0; i < size; i++) values[i] = rs.getLong(i + 1);
        return values;
    }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column); return value == null ? null : value.toInstant();
    }
}

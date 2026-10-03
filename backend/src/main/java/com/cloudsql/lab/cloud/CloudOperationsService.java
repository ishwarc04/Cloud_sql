package com.cloudsql.lab.cloud;

import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class CloudOperationsService {
    private final JdbcTemplate jdbc;
    private final Queue<HealthSample> pending = new ConcurrentLinkedQueue<>();
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CloudOperationsService.class);
    public CloudOperationsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Hour(Instant hour, long requests, long errors, double averageLatencyMs, long maxLatencyMs) { }
    public record HealthSample(String id, Instant checkedAt, String status, long latencyMs) { }
    public record AuditEvent(String id, String actorName, String action, String target, String outcome, Instant occurredAt) { }
    public record Usage(String userId, String name, long requests, long errors, long workspaceRequests, long backupCount, long backupBytes) { }
    public record Report(List<Hour> hours, List<HealthSample> health, List<AuditEvent> audit, List<Usage> usage,
            long totalRequests, long failedRequests, double averageLatencyMs, double probeSuccessPercent, long probeCount,
            long backupCount, long backupBytes, Instant generatedAt) { }
    public void recordRequest(String actor, String group, int status, long duration) {
        Timestamp hour = Timestamp.from(Instant.now().truncatedTo(ChronoUnit.HOURS)); String user = actor == null ? "anonymous" : actor;
        long error = status >= 400 ? 1 : 0;
        String update = "UPDATE platform.request_metrics SET requests = requests + 1, errors = errors + ?, duration_ms = duration_ms + ?, max_duration_ms = CASE WHEN max_duration_ms < ? THEN ? ELSE max_duration_ms END WHERE bucket_start = ? AND route_group = ? AND user_id = ?";
        try {
            if (jdbc.update(update, error, duration, duration, duration, hour, group, user) == 0) {
                try { jdbc.update("INSERT INTO platform.request_metrics VALUES (?, ?, ?, 1, ?, ?, ?)", hour, group, user, error, duration, duration); }
                catch (DuplicateKeyException exception) { jdbc.update(update, error, duration, duration, duration, hour, group, user); }
            }
        } catch (RuntimeException exception) { log.warn("Request metrics could not be persisted ({})", exception.getClass().getSimpleName()); }
    }
    public void probe() {
        Instant checked = Instant.now(); long started = System.nanoTime(); String status;
        try { jdbc.queryForObject("SELECT 1", Integer.class); status = "UP"; }
        catch (RuntimeException exception) { status = "DOWN"; }
        pending.add(new HealthSample(UUID.randomUUID().toString(), checked, status, Math.max(1, (System.nanoTime() - started) / 1_000_000)));
        while (pending.size() > 1440) pending.poll();
        for (HealthSample sample; (sample = pending.peek()) != null;) {
            try { jdbc.update("INSERT INTO platform.health_samples VALUES (?, ?, ?, ?)", sample.id(), Timestamp.from(sample.checkedAt()), sample.status(), sample.latencyMs()); pending.poll(); }
            catch (DuplicateKeyException exception) { pending.poll(); }
            catch (RuntimeException exception) { return; }
        }
        Timestamp retention = Timestamp.from(Instant.now().minus(7, ChronoUnit.DAYS));
        jdbc.update("DELETE FROM platform.health_samples WHERE checked_at < ?", retention);
        jdbc.update("DELETE FROM platform.request_metrics WHERE bucket_start < ?", retention);
        jdbc.update("DELETE FROM platform.audit_events WHERE occurred_at < ?", Timestamp.from(Instant.now().minus(30, ChronoUnit.DAYS)));
    }
    public Report report() {
        Instant now = Instant.now(), first = now.truncatedTo(ChronoUnit.HOURS).minus(23, ChronoUnit.HOURS);
        Map<Instant, Hour> buckets = new HashMap<>();
        jdbc.query("SELECT bucket_start, SUM(requests), SUM(errors), SUM(duration_ms), MAX(max_duration_ms) FROM platform.request_metrics WHERE bucket_start >= ? GROUP BY bucket_start", rs -> {
            long count = rs.getLong(2); Instant hour = rs.getTimestamp(1).toInstant();
            buckets.put(hour, new Hour(hour, count, rs.getLong(3), count == 0 ? 0 : (double) rs.getLong(4) / count, rs.getLong(5)));
        }, Timestamp.from(first));
        List<Hour> hours = java.util.stream.IntStream.range(0, 24).mapToObj(i -> buckets.getOrDefault(first.plus(i, ChronoUnit.HOURS), new Hour(first.plus(i, ChronoUnit.HOURS), 0, 0, 0, 0))).toList();
        List<HealthSample> health = jdbc.query("SELECT * FROM platform.health_samples WHERE checked_at >= ? ORDER BY checked_at DESC LIMIT 1440", (rs, row) -> new HealthSample(rs.getString("id"), rs.getTimestamp("checked_at").toInstant(), rs.getString("status"), rs.getLong("latency_ms")), Timestamp.from(now.minus(24, ChronoUnit.HOURS)));
        List<AuditEvent> audit = jdbc.query("SELECT a.*, COALESCE(u.name, 'Anonymous / removed account') AS actor_name FROM platform.audit_events a LEFT JOIN platform.users u ON u.id = a.actor_id ORDER BY a.occurred_at DESC LIMIT 100", (rs, row) -> new AuditEvent(rs.getString("id"), rs.getString("actor_name"), rs.getString("action"), rs.getString("target"), rs.getString("outcome"), rs.getTimestamp("occurred_at").toInstant()));
        List<Usage> usage = jdbc.query("""
            SELECT u.id, u.name, COALESCE(m.requests, 0), COALESCE(m.errors, 0), COALESCE(m.workspace_requests, 0), COALESCE(b.backups, 0), COALESCE(b.bytes, 0)
            FROM platform.users u
            LEFT JOIN (SELECT user_id, SUM(requests) AS requests, SUM(errors) AS errors, SUM(CASE WHEN route_group = 'WORKSPACE' THEN requests ELSE 0 END) AS workspace_requests FROM platform.request_metrics WHERE bucket_start >= ? GROUP BY user_id) m ON m.user_id = u.id
            LEFT JOIN (SELECT owner_user_id, COUNT(*) AS backups, SUM(size_bytes) AS bytes FROM platform.workspace_backups GROUP BY owner_user_id) b ON b.owner_user_id = u.id
            ORDER BY COALESCE(m.requests, 0) DESC, u.created_at DESC LIMIT 200
            """, (rs, row) -> new Usage(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getLong(5), rs.getLong(6), rs.getLong(7)), Timestamp.from(first));
        long requests = hours.stream().mapToLong(Hour::requests).sum(), errors = hours.stream().mapToLong(Hour::errors).sum();
        long[] backups = jdbc.queryForObject("SELECT COUNT(*), COALESCE(SUM(size_bytes),0) FROM platform.workspace_backups", (rs, row) -> new long[]{rs.getLong(1), rs.getLong(2)});
        return new Report(hours, health, audit, usage, requests, errors, requests == 0 ? 0 : hours.stream().mapToDouble(h -> h.averageLatencyMs() * h.requests()).sum() / requests,
            health.isEmpty() ? 0 : health.stream().filter(s -> s.status().equals("UP")).count() * 100.0 / health.size(), health.size(), backups[0], backups[1], now);
    }
}

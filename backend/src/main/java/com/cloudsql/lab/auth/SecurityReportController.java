package com.cloudsql.lab.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
public class SecurityReportController {
    private final JdbcTemplate jdbc;
    private final ApiRateLimiter limiter;
    public SecurityReportController(JdbcTemplate jdbc, ApiRateLimiter limiter) {this.jdbc=jdbc;this.limiter=limiter;}
    public record Event(String id,String actor,String action,String outcome,Instant occurredAt) {}
    public record Report(long failedLogins,long blockedLogins,long rateLimitedRequests,long revokedSessions,long activeSessions,int requestsPerMinute,List<Event> events) {}
    private long events(String action,String outcome,Timestamp since) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM platform.audit_events WHERE action=? AND outcome=? AND occurred_at>=?",Long.class,action,outcome,since);
    }
    @GetMapping("/api/admin/security") public Report report() {
        var since=Timestamp.from(Instant.now().minus(1,ChronoUnit.DAYS));
        var recent=jdbc.query("SELECT a.id,u.name,a.action,a.outcome,a.occurred_at FROM platform.audit_events a LEFT JOIN platform.users u ON u.id=a.actor_id WHERE a.occurred_at>=? AND a.action IN ('SIGN_IN','LOGIN_BLOCKED','API_RATE_LIMITED','SESSION_REVOKED') ORDER BY a.occurred_at DESC LIMIT 100",
            (r,i)->new Event(r.getString("id"),r.getString("name")==null?"Unauthenticated":r.getString("name"),r.getString("action"),r.getString("outcome"),r.getTimestamp("occurred_at").toInstant()),since);
        return new Report(events("SIGN_IN","FAILED",since),events("LOGIN_BLOCKED","BLOCKED",since),
            jdbc.queryForObject("SELECT COALESCE(SUM(blocked),0) FROM platform.api_limits WHERE bucket_start>=?",Long.class,since),
            events("SESSION_REVOKED","SUCCESS",since),jdbc.queryForObject("SELECT COUNT(*) FROM platform.sessions WHERE expires_at>?",Long.class,Timestamp.from(Instant.now())),limiter.limit,recent);
    }
}

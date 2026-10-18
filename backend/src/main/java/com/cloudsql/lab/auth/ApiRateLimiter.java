package com.cloudsql.lab.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.cloudsql.lab.cloud.AuditService;

@Service
public class ApiRateLimiter {
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    public final int limit;
    public ApiRateLimiter(JdbcTemplate jdbc, AuditService audit,
            @Value("${cloudsql.security.requests-per-minute:120}") int limit) {
        if (limit < 10 || limit > 10000) throw new IllegalArgumentException("API limit must be 10–10000");
        this.jdbc=jdbc; this.audit=audit; this.limit=limit;
    }
    public boolean allow(String subject, String actor) {
        var bucket=Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MINUTES));
        // Conditional increments are atomic across Render instances; no client IP headers are trusted.
        if (jdbc.update("UPDATE platform.api_limits SET requests=requests+1 WHERE subject=? AND bucket_start=? AND requests < ?",subject,bucket,limit)==1) return true;
        if (jdbc.queryForObject("SELECT COUNT(*) FROM platform.api_limits WHERE subject=? AND bucket_start=?",Integer.class,subject,bucket)==0) {
            try { jdbc.update("INSERT INTO platform.api_limits (subject,bucket_start,requests,blocked) VALUES (?,?,1,0)",subject,bucket); return true; }
            catch (DuplicateKeyException race) { /* Another instance created this minute's counter. */ }
        }
        // A concurrent request may create the row between our initial UPDATE and SELECT.
        // Retry the atomic increment before treating an existing counter as exhausted.
        if (jdbc.update("UPDATE platform.api_limits SET requests=requests+1 WHERE subject=? AND bucket_start=? AND requests < ?",subject,bucket,limit)==1) return true;
        // One audit event per subject/minute; repeated denials remain counted without flooding the audit table.
        if (jdbc.update("UPDATE platform.api_limits SET blocked=1 WHERE subject=? AND bucket_start=? AND blocked=0",subject,bucket)==1)
            audit.record(actor,"API_RATE_LIMITED","API","BLOCKED");
        else jdbc.update("UPDATE platform.api_limits SET blocked=blocked+1 WHERE subject=? AND bucket_start=?",subject,bucket);
        return false;
    }
    @Scheduled(fixedDelay=3600000)
    public void cleanup() { jdbc.update("DELETE FROM platform.api_limits WHERE bucket_start < ?",Timestamp.from(Instant.now().minus(1,ChronoUnit.DAYS))); }
}

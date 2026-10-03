package com.cloudsql.lab.cloud;

import com.cloudsql.lab.database.WorkspaceProperties;
import com.cloudsql.lab.progress.CurrentUserProvider;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountPlanService {
    private final JdbcTemplate jdbc;
    private final CurrentUserProvider current;
    private final WorkspaceProperties defaults;
    private final AuditService audit;
    public AccountPlanService(JdbcTemplate jdbc, CurrentUserProvider current, WorkspaceProperties defaults, AuditService audit) {
        this.jdbc = jdbc; this.current = current; this.defaults = defaults; this.audit = audit;
    }
    public record Plan(String name, int maximumDatabases, long storagePerDatabaseBytes) { }
    public record Payment(String requestId, int amountPaise, String status, Instant createdAt) { }
    public record Billing(Plan plan, List<Payment> payments, boolean demoOnly) { }
    public Plan forUser(String user) {
        boolean pro = jdbc.queryForObject("SELECT COUNT(*) FROM platform.account_plans WHERE user_id = ?", Integer.class, user) > 0;
        return pro ? new Plan("PRO", Math.max(5, defaults.maxDatabasesPerUser()), Math.max(50L * 1024 * 1024, defaults.storageLimitBytes()))
            : new Plan("FREE", defaults.maxDatabasesPerUser(), defaults.storageLimitBytes());
    }
    public Billing billing() {
        String user = current.currentUserId();
        return new Billing(forUser(user), jdbc.query("SELECT request_id, amount_paise, status, created_at FROM platform.demo_payments WHERE user_id = ? ORDER BY created_at DESC LIMIT 10",
            (rs, row) -> new Payment(rs.getString(1), rs.getInt(2), rs.getString(3), rs.getTimestamp(4).toInstant()), user), true);
    }
    @Transactional public Billing upgrade(String requestId, boolean declined) {
        String user = current.currentUserId();
        jdbc.queryForObject("SELECT id FROM platform.users WHERE id = ? FOR UPDATE", String.class, user);
        if (forUser(user).name().equals("PRO") || jdbc.queryForObject("SELECT COUNT(*) FROM platform.demo_payments WHERE user_id = ? AND request_id = ?", Integer.class, user, requestId) > 0) return billing();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO platform.demo_payments VALUES (?, ?, 19900, ?, ?)", user, requestId, declined ? "DECLINED" : "SUCCESS", now);
        if (!declined) {
            jdbc.update("INSERT INTO platform.account_plans VALUES (?, 'PRO', ?)", user, now);
            long limit = forUser(user).storagePerDatabaseBytes();
            jdbc.update("UPDATE platform.database_workspaces SET storage_limit_bytes = CASE WHEN storage_limit_bytes < ? THEN ? ELSE storage_limit_bytes END WHERE owner_user_id = ?", limit, limit, user);
        }
        audit.recordInTransaction(user, "DEMO_PAYMENT", "PRO", declined ? "DECLINED" : "SUCCESS");
        return billing();
    }
}

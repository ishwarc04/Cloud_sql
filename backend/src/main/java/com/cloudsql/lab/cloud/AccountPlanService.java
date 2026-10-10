package com.cloudsql.lab.cloud;

import com.cloudsql.lab.database.WorkspaceProperties;
import com.cloudsql.lab.progress.CurrentUserProvider;

import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;


@Service
public class AccountPlanService {
    private final JdbcTemplate jdbc;
    private final CurrentUserProvider current;
    private final WorkspaceProperties defaults;

    public AccountPlanService(JdbcTemplate jdbc, CurrentUserProvider current, WorkspaceProperties defaults) {
        this.jdbc = jdbc; this.current = current; this.defaults = defaults;
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
}

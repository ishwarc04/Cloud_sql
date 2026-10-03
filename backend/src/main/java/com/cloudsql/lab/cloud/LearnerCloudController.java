package com.cloudsql.lab.cloud;

import com.cloudsql.lab.progress.CurrentUserProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
public class LearnerCloudController {
    private final JdbcTemplate jdbc;
    private final CurrentUserProvider current;
    private final AccountPlanService plans;
    public LearnerCloudController(JdbcTemplate jdbc, CurrentUserProvider current, AccountPlanService plans) { this.jdbc = jdbc; this.current = current; this.plans = plans; }
    public record Checkout(@NotBlank @Pattern(regexp="[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}") String requestId, boolean simulateFailure) { }
    @GetMapping("/api/billing") public AccountPlanService.Billing billing() { return plans.billing(); }
    @PostMapping("/api/billing/upgrade") public AccountPlanService.Billing upgrade(@Valid @RequestBody Checkout input) { return plans.upgrade(input.requestId(), input.simulateFailure()); }
    public record Leader(long rank, String name, long points, long solved, boolean you, String reward) { }
    public record Board(List<Leader> leaders, Leader yourRank) { }
    private static final String RANKED = """
        WITH totals AS (
          SELECT u.id, u.name, u.created_at, COALESCE(SUM(p.points),0) AS points,
            SUM(CASE WHEN p.status = 'SOLVED' THEN 1 ELSE 0 END) AS solved
          FROM platform.users u LEFT JOIN platform.user_problem_progress p ON p.user_id = u.id
          WHERE u.role = 'USER' GROUP BY u.id, u.name, u.created_at
        ), ranked AS (
          SELECT *, ROW_NUMBER() OVER (ORDER BY points DESC, solved DESC, created_at, id) AS position FROM totals
        ) SELECT position, name, points, solved, id FROM ranked
        """;
    @GetMapping("/api/leaderboard") public Board leaderboard() {
        String user = current.currentUserId();
        org.springframework.jdbc.core.RowMapper<Leader> mapper = (rs, row) -> {
            long rank = rs.getLong(1), points = rs.getLong(3);
            String reward = points <= 0 || rank > 3 ? "" : rank == 1 ? "SQL Champion · Gold trophy" : rank == 2 ? "Query Master · Silver trophy" : "Rising Star · Bronze trophy";
            return new Leader(rank, rs.getString(2), points, rs.getLong(4), user.equals(rs.getString(5)), reward);
        };
        var leaders = jdbc.query(RANKED + " ORDER BY position LIMIT 100", mapper);
        var own = jdbc.query(RANKED + " WHERE id = ?", mapper, user);
        return new Board(leaders, own.isEmpty() ? null : own.getFirst());
    }
}

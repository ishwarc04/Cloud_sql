package com.cloudsql.lab.progress;

import com.cloudsql.lab.problem.ProblemRepository;
import com.cloudsql.lab.problem.model.ProblemDefinition;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
public class DashboardController {
    private final JdbcTemplate jdbc;
    private final CurrentUserProvider user;
    private final ProgressRepository progress;
    private final ProblemRepository problems;
    public DashboardController(JdbcTemplate jdbc, CurrentUserProvider user, ProgressRepository progress, ProblemRepository problems) {
        this.jdbc = jdbc; this.user = user; this.progress = progress; this.problems = problems;
    }
    public record Breakdown(String name, long solved, long total) { }
    public record Submission(String id, long problemId, String title, String difficulty, String category, String outcome, int pointsAwarded, Instant submittedAt) { }
    public record Dashboard(int totalPoints, long solvedProblems, int attemptCount, List<Breakdown> difficultyProgress,
            List<Breakdown> categoryProgress, List<Submission> recentSubmissions) { }
    @GetMapping("/api/dashboard") public Dashboard dashboard() {
        String id = user.currentUserId();
        Map<Long, ProblemProgress> records = progress.findByUserId(id);
        int points = jdbc.queryForObject("SELECT COALESCE(SUM(points), 0) FROM platform.user_problem_progress WHERE user_id = ?", Integer.class, id);
        List<Submission> recent = jdbc.query("SELECT id, problem_id, outcome, points_awarded, submitted_at FROM platform.submissions WHERE user_id = ? ORDER BY submitted_at DESC, id DESC LIMIT 10",
                (rs, row) -> {
                    ProblemDefinition problem = problems.findById(rs.getLong("problem_id")).orElseThrow();
                    return new Submission(rs.getString("id"), problem.id(), problem.title(), problem.difficulty(), problem.topic(), rs.getString("outcome"), rs.getInt("points_awarded"), rs.getTimestamp("submitted_at").toInstant());
                }, id);
        return new Dashboard(points, records.values().stream().filter(p -> p.status().equals("SOLVED")).count(),
                records.values().stream().mapToInt(ProblemProgress::attempts).sum(),
                breakdown(List.of("Easy", "Medium", "Hard"), ProblemDefinition::difficulty, records),
                breakdown(List.of("Basic Select", "Advanced Select", "Aggregation", "Basic Join", "Advanced Join", "Alternative Queries"), ProblemDefinition::topic, records), recent);
    }
    private List<Breakdown> breakdown(List<String> names, Function<ProblemDefinition, String> field, Map<Long, ProblemProgress> records) {
        return names.stream().map(name -> new Breakdown(name,
                problems.findAll().stream().filter(p -> field.apply(p).equals(name) && records.containsKey(p.id()) && records.get(p.id()).status().equals("SOLVED")).count(),
                problems.findAll().stream().filter(p -> field.apply(p).equals(name)).count())).toList();
    }
}

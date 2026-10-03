package com.cloudsql.lab.admin;

import static org.assertj.core.api.Assertions.*;
import com.cloudsql.lab.TestAccounts;
import com.cloudsql.lab.auth.*;
import java.net.URI;
import java.net.http.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminAnalyticsIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired AdminAnalyticsService analytics;
    @Autowired AuthService auth;
    @Autowired PasswordHasher passwords;
    private final HttpClient client = HttpClient.newHttpClient();
    String adminCookie, learnerCookie, otherCookie;
    Account admin, learner, other;
    @BeforeEach void setup() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        new AdminProvisioner(jdbc, passwords, email, TestAccounts.PASSWORD).run(null);
        adminCookie = TestAccounts.login(port, email);
        learnerCookie = TestAccounts.signup(port); otherCookie = TestAccounts.signup(port);
        admin = account(adminCookie); learner = account(learnerCookie); other = account(otherCookie);
    }
    @AfterEach void cleanup() {
        for (Account user : List.of(admin, learner, other)) {
            jdbc.update("DELETE FROM platform.database_workspaces WHERE owner_user_id = ?", user.id());
            jdbc.update("DELETE FROM platform.user_problem_progress WHERE user_id = ?", user.id());
            jdbc.update("DELETE FROM platform.users WHERE id = ?", user.id());
        }
    }
    @Test void onlyAdministratorsCanReadAnalyticsOrAccountDetails() throws Exception {
        for (String path : new String[]{"/api/admin/analytics", "/api/admin/users/" + learner.id()}) {
            assertThat(get(path, null).statusCode()).isEqualTo(401);
            assertThat(get(path, learnerCookie).statusCode()).isEqualTo(403);
            assertThat(get(path, adminCookie).statusCode()).isEqualTo(200);
        }
        assertThat(get("/api/admin/users/" + UUID.randomUUID(), adminCookie).statusCode()).isEqualTo(404);
    }
    @Test void accountMetricsNeverMultiplyWhenJoiningSubmissionsAndDatabases() throws Exception {
        var before = analytics.snapshot().summary();
        seed();
        var report = analytics.snapshot();
        assertThat(report.summary().totalSubmissions()).isEqualTo(before.totalSubmissions() + 4);
        assertThat(report.summary().acceptedSubmissions()).isEqualTo(before.acceptedSubmissions() + 2);
        assertThat(report.summary().wrongAnswers()).isEqualTo(before.wrongAnswers() + 1);
        assertThat(report.summary().queryErrors()).isEqualTo(before.queryErrors() + 1);
        assertThat(report.summary().totalPoints()).isEqualTo(before.totalPoints() + 10);
        assertThat(report.summary().solvedUserProblems()).isEqualTo(before.solvedUserProblems() + 1);
        assertThat(report.summary().totalWorkspaces()).isEqualTo(before.totalWorkspaces() + 2);
        var detail = analytics.user(learner.id());
        assertThat(detail.user().points()).isEqualTo(10);
        assertThat(detail.user().attempts()).isEqualTo(4);
        assertThat(detail.user().solvedProblems()).isEqualTo(1);
        assertThat(detail.user().databaseCount()).isEqualTo(2);
        assertThat(detail.user().storageUsedBytes()).isEqualTo(300);
        assertThat(detail.workspaces()).hasSize(2).allMatch(w -> w.ownerName().equals(learner.name()));
        assertThat(detail.recentSubmissions()).hasSize(4);
        assertThat(analytics.user(other.id()).user().points()).isZero();
        assertThat(analytics.user(other.id()).workspaces()).isEmpty();
        String body = get("/api/admin/analytics", adminCookie).body();
        assertThat(body).contains(learner.email(), "Private fixture one", "Basic Select", "WRONG_ANSWER");
        assertThat(body).doesNotContain("password_hash", "token_hash", "internal_workspace_id", "sensitive-query-marker", "jdbc:", "query_text");
    }
    @Test void chartsFillEmptyUtcDatesAndExpiredSessionsAreExcluded() {
        seed();
        var today = Instant.now().atZone(ZoneOffset.UTC).toLocalDate();
        jdbc.update("INSERT INTO platform.sessions (token_hash, user_id, created_at, expires_at) VALUES (?, ?, ?, ?)", "a".repeat(64), learner.id(), Timestamp.from(Instant.now().minusSeconds(100)), Timestamp.from(Instant.now().minusSeconds(1)));
        var report = analytics.snapshot();
        assertThat(report.dailyActivity()).hasSize(14);
        assertThat(report.dailyActivity().getFirst().date()).isEqualTo(today.minusDays(13));
        assertThat(report.dailyActivity().getLast().date()).isEqualTo(today);
        assertThat(report.dailyActivity().getLast().submissions()).isGreaterThanOrEqualTo(4);
        assertThat(report.problems()).hasSize(15);
        var problem = report.problems().stream().filter(p -> p.id() == 4).findFirst().orElseThrow();
        assertThat(problem.accepted()).isGreaterThanOrEqualTo(2);
        assertThat(problem.solvedUsers()).isGreaterThanOrEqualTo(1);
        assertThat(analytics.user(learner.id()).user().activeSessions()).isEqualTo(1);
        assertThat(report.quotas().maxDatabasesPerUser()).isEqualTo(2);
        assertThat(report.quotas().queryTimeoutSeconds()).isEqualTo(5);
    }
    @Test void tableLimitsDoNotTruncateAggregateTotals() {
        // Recent activity is intentionally bounded while aggregate counts cover full history.
        for (int i = 0; i < 55; i++) submission("WRONG_ANSWER", 0);
        assertThat(analytics.user(learner.id()).recentSubmissions()).hasSize(50);
        assertThat(analytics.snapshot().recentSubmissions()).hasSize(50);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform.submissions WHERE user_id = ?", Integer.class, learner.id())).isEqualTo(55);
    }
    private void seed() {
        jdbc.update("INSERT INTO platform.user_problem_progress (user_id, problem_id, status, attempts, points, solved_at) VALUES (?, 4, 'SOLVED', 4, 10, ?)", learner.id(), Timestamp.from(Instant.now()));
        submission("ACCEPTED", 10); submission("ACCEPTED", 0); submission("WRONG_ANSWER", 0); submission("ERROR", 0);
        for (int i = 0; i < 2; i++) jdbc.update("INSERT INTO platform.database_workspaces (id, internal_workspace_id, name, owner_user_id, status, created_at, storage_used_bytes, storage_limit_bytes) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, 10485760)", UUID.randomUUID().toString(), UUID.randomUUID().toString().replace("-", ""), i == 0 ? "Private fixture one" : "Private fixture two", learner.id(), Timestamp.from(Instant.now()), (i + 1) * 100);
    }
    private void submission(String outcome, int points) {
        jdbc.update("INSERT INTO platform.submissions (id, user_id, problem_id, query_text, outcome, points_awarded, submitted_at) VALUES (?, ?, 4, 'sensitive-query-marker', ?, ?, ?)", UUID.randomUUID().toString(), learner.id(), outcome, points, Timestamp.from(Instant.now()));
    }
    private Account account(String cookie) { return auth.resolve(cookie.split("=", 2)[1]); }
    private HttpResponse<String> get(String path, String cookie) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (cookie != null) request.header("Cookie", cookie);
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}

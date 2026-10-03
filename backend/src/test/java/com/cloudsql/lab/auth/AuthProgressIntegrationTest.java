package com.cloudsql.lab.auth;

import static org.assertj.core.api.Assertions.*;
import com.cloudsql.lab.TestAccounts;
import com.cloudsql.lab.problem.ProblemRepository;
import java.net.URI;
import java.net.http.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthProgressIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired PasswordHasher passwords;
    @Autowired ProblemRepository problems;
    private final HttpClient client = HttpClient.newHttpClient();
    String cookie;
    Account user;
    @BeforeEach void signup() throws Exception {
        cookie = TestAccounts.signup(port);
        user = auth.resolve(cookie.split("=", 2)[1]);
    }
    @Test void publicSignupCannotCreateAdminAndStoresOnlyHashes() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        var response = send("POST", "/api/auth/signup", "{\"name\":\"Public user\",\"email\":\"" + email + "\",\"password\":\"" + TestAccounts.PASSWORD + "\",\"role\":\"ADMIN\"}", null);
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains("\"role\":\"USER\"").doesNotContain("password", "token");
        String header = response.headers().firstValue("set-cookie").orElseThrow();
        assertThat(header).contains("HttpOnly", "SameSite=Lax", "Path=/");
        String stored = jdbc.queryForObject("SELECT password_hash FROM platform.users WHERE id = ?", String.class, user.id());
        assertThat(stored).doesNotContain(TestAccounts.PASSWORD);
        assertThat(passwords.matches(TestAccounts.PASSWORD, stored)).isTrue();
        assertThat(jdbc.queryForObject("SELECT token_hash FROM platform.sessions WHERE user_id = ?", String.class, user.id()))
                .hasSize(64).doesNotContain(cookie.split("=", 2)[1]);
        assertThat(send("GET", "/api/auth/me", null, cookie).body()).contains(user.email()).doesNotContain("password", "token");
        assertThat(send("POST", "/api/auth/signup", "{\"name\":\"X\",\"email\":\"" + user.email().toUpperCase() + "\",\"password\":\"" + TestAccounts.PASSWORD + "\"}", null).statusCode()).isEqualTo(409);
    }
    @Test void loginRotatesSessionsAndLogoutRevokesThem() throws Exception {
        assertThat(send("POST", "/api/auth/login", "{\"email\":\"" + user.email() + "\",\"password\":\"wrong-password\"}", null).statusCode()).isEqualTo(401);
        var login = send("POST", "/api/auth/login", "{\"email\":\"" + user.email() + "\",\"password\":\"" + TestAccounts.PASSWORD + "\"}", cookie);
        assertThat(login.statusCode()).isEqualTo(200);
        String rotated = login.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0];
        assertThat(rotated).isNotEqualTo(cookie);
        assertThat(send("GET", "/api/dashboard", null, cookie).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/dashboard", null, rotated).statusCode()).isEqualTo(200);
        assertThat(send("POST", "/api/auth/logout", "", rotated).statusCode()).isEqualTo(204);
        assertThat(send("GET", "/api/auth/me", null, rotated).statusCode()).isEqualTo(401);
    }
    @Test void anonymousExpiredAndUserSessionsCannotReachProtectedApis() throws Exception {
        for (String path : new String[]{"/api/problems", "/api/databases", "/api/dashboard", "/api/admin/overview"})
            assertThat(send("GET", path, null, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/admin/overview", null, cookie).statusCode()).isEqualTo(403);
        jdbc.update("UPDATE platform.sessions SET expires_at = ? WHERE user_id = ?", Timestamp.from(Instant.now().minusSeconds(1)), user.id());
        assertThat(send("GET", "/api/auth/me", null, cookie).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/health", null, null).statusCode()).isEqualTo(200);
    }
    @Test void corsAndCsrfRequireExactOriginAndCustomHeader() throws Exception {
        var denied = client.send(builder("/api/dashboard").header("Cookie", cookie).header("Origin", "https://untrusted.example").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(denied.statusCode()).isEqualTo(403);
        var csrf = client.send(builder("/api/auth/logout").header("Cookie", cookie).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(csrf.statusCode()).isEqualTo(403);
        var preflight = client.send(builder("/api/problems/4/submit").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "content-type,x-cloudsql-request")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(preflight.statusCode()).isEqualTo(200);
        assertThat(preflight.headers().firstValue("access-control-allow-origin")).contains("http://localhost:5173");
        assertThat(preflight.headers().firstValue("access-control-allow-credentials")).contains("true");
    }
    @Test void concurrentSuccessfulSolutionsAwardExactlyOnce() throws Exception {
        String body = query(problems.findById(4).orElseThrow().solutionQuery());
        var pending = new ArrayList<java.util.concurrent.CompletableFuture<HttpResponse<String>>>();
        for (int i = 0; i < 8; i++) pending.add(client.sendAsync(builder("/api/problems/4/submit").header("Cookie", cookie)
                .header("X-CloudSQL-Request", "1").header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString()));
        for (var request : pending) assertThat(request.join().statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/dashboard", null, cookie).body()).contains("\"totalPoints\":10", "\"solvedProblems\":1", "\"attemptCount\":8");
        assertThat(jdbc.queryForObject("SELECT SUM(points_awarded) FROM platform.submissions WHERE user_id = ?", Integer.class, user.id())).isEqualTo(10);
    }
    @Test void allOriginalProblemsHaveExecutableSolutionsAndExpectedPoints() throws Exception {
        int total = 0;
        for (var problem : problems.findAll()) {
            if (problem.id() < 4) continue;
            int points = com.cloudsql.lab.problem.ProblemService.pointsFor(problem.difficulty());
            var accepted = send("POST", "/api/problems/" + problem.id() + "/submit", query(problem.solutionQuery()), cookie);
            assertThat(accepted.statusCode()).as(problem.title() + accepted.body()).isEqualTo(200);
            assertThat(accepted.body()).contains("\"correct\":true", "\"pointsAwarded\":" + points);
            var repeated = send("POST", "/api/problems/" + problem.id() + "/submit", query(problem.solutionQuery()), cookie);
            assertThat(repeated.body()).contains("\"correct\":true", "\"pointsAwarded\":0");
            total += points;
        }
        assertThat(send("GET", "/api/dashboard", null, cookie).body()).contains("\"totalPoints\":" + total, "\"solvedProblems\":12", "\"attemptCount\":24", "Basic Select", "Alternative Queries");
        var alternative = send("POST", "/api/problems/15/submit", query("SELECT m.id, m.name FROM members m JOIN rentals r ON r.member_id = m.id JOIN tools t ON t.id = r.tool_id WHERE t.kind = 'Textile' GROUP BY m.id, m.name HAVING COUNT(DISTINCT t.id) = (SELECT COUNT(*) FROM tools WHERE kind = 'Textile') ORDER BY m.id"), cookie);
        assertThat(alternative.body()).contains("\"correct\":true", "\"pointsAwarded\":0");
    }
    @Test void errorsAndWrongAnswersCountAndSolvedStatusNeverRegresses() throws Exception {
        assertThat(send("POST", "/api/problems/5/submit", query("SELECT missing FROM members"), cookie).statusCode()).isEqualTo(400);
        send("POST", "/api/problems/5/submit", query("SELECT 1"), cookie);
        send("POST", "/api/problems/5/submit", query(problems.findById(5).orElseThrow().solutionQuery()), cookie);
        send("POST", "/api/problems/5/submit", query("SELECT 1"), cookie);
        assertThat(send("GET", "/api/dashboard", null, cookie).body()).contains("\"totalPoints\":10", "\"attemptCount\":4", "WRONG_ANSWER", "ERROR", "ACCEPTED");
        assertThat(send("GET", "/api/problems/5", null, cookie).body()).contains("\"status\":\"SOLVED\"");
    }
    @Test void accountsHaveSeparateWorkspacesProgressAndSubmissionHistory() throws Exception {
        var created = send("POST", "/api/databases", "{\"name\":\"Private workshop\"}", cookie);
        assertThat(created.statusCode()).isEqualTo(201);
        String id = created.body().split("\"id\":\"")[1].split("\"")[0];
        send("POST", "/api/problems/4/submit", query(problems.findById(4).orElseThrow().solutionQuery()), cookie);
        String other = TestAccounts.signup(port);
        for (String suffix : new String[]{"", "/schema"}) assertThat(send("GET", "/api/databases/" + id + suffix, null, other).statusCode()).isEqualTo(404);
        assertThat(send("DELETE", "/api/databases/" + id, "", other).statusCode()).isEqualTo(404);
        assertThat(send("POST", "/api/databases/" + id + "/execute", query("SELECT 1"), other).statusCode()).isEqualTo(404);
        assertThat(send("GET", "/api/dashboard", null, other).body()).contains("\"totalPoints\":0", "\"recentSubmissions\":[]");
        assertThat(send("GET", "/api/databases", null, other).body()).doesNotContain("Private workshop");
        send("DELETE", "/api/databases/" + id, "", cookie);
    }
    @Test void SQLCannotReachCredentialsOrChangeDatabaseSession() throws Exception {
        for (String sql : new String[]{"SELECT set_config('search_path', 'platform', false)", "SELECT * FROM \"platform\".\"users\"", "SELECT pg_read_file('/etc/passwd')", "SELECT * FROM U&\"pl\\0061tform\".users", "SELECT query_to_xml('SELECT * FROM platform.users', true, false, '')"}) {
            assertThat(send("POST", "/api/problems/4/execute", query(sql), cookie).statusCode()).as(sql).isEqualTo(400);
        }
    }
    @Test void initializationAndAdminProvisioningAreIdempotent() {
        var populator = new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.ClassPathResource("schema.sql"));
        populator.execute(jdbc.getDataSource()); populator.execute(jdbc.getDataSource());
        assertThat(auth.resolve(cookie.split("=", 2)[1])).isEqualTo(user);
        String email = UUID.randomUUID() + "@example.test";
        var provisioning = new AdminProvisioner(jdbc, passwords, email, TestAccounts.PASSWORD);
        provisioning.run(null);
        String hash = jdbc.queryForObject("SELECT password_hash FROM platform.users WHERE email = ?", String.class, email);
        provisioning.run(null);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM platform.users WHERE email = ?", String.class, email)).isEqualTo(hash);
        assertThatThrownBy(() -> new AdminProvisioner(jdbc, passwords, user.email(), TestAccounts.PASSWORD).run(null)).isInstanceOf(IllegalStateException.class);
    }
    @Test void repeatedInvalidLoginsAreThrottled() throws Exception {
        String body = "{\"email\":\"" + user.email() + "\",\"password\":\"wrong-password\"}";
        for (int i = 0; i < 10; i++) assertThat(send("POST", "/api/auth/login", body, null).statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/auth/login", body, null).statusCode()).isEqualTo(429);
    }
    private HttpRequest.Builder builder(String path) { return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)); }
    private HttpResponse<String> send(String method, String path, String body, String session) throws Exception {
        var builder = builder(path).header("Content-Type", "application/json").header("X-CloudSQL-Request", "1");
        if (session != null) builder.header("Cookie", session);
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private String query(String sql) { return "{\"query\":\"" + sql.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}"; }
}

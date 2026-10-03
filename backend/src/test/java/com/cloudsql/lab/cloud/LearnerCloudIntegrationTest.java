package com.cloudsql.lab.cloud;

import com.cloudsql.lab.TestAccounts;
import com.cloudsql.lab.auth.*;
import com.cloudsql.lab.database.*;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LearnerCloudIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired PasswordHasher passwords;
    @Autowired WorkspaceCatalogRepository catalog;
    @Autowired WorkspaceEngine engine;
    String learner, other, admin, owner;
    List<String> users;
    final HttpClient client = HttpClient.newHttpClient();
    final tools.jackson.databind.json.JsonMapper json = tools.jackson.databind.json.JsonMapper.builder().build();
    @BeforeEach void setup() throws Exception {
        learner = TestAccounts.signup(port); other = TestAccounts.signup(port);
        String email = UUID.randomUUID() + "@example.test";
        new AdminProvisioner(jdbc, passwords, email, TestAccounts.PASSWORD).run(null);
        admin = TestAccounts.login(port, email); owner = user(learner);
        users = List.of(owner, user(other), user(admin));
    }
    String user(String cookie) { return auth.resolve(cookie.substring(cookie.indexOf('=') + 1)).id(); }
    @AfterEach void cleanup() {
        for (String id : users) {
            for (var workspace : catalog.findByOwner(id)) { engine.delete(workspace); catalog.delete(workspace.id()); }
            jdbc.update("DELETE FROM platform.user_problem_progress WHERE user_id = ?", id);
            jdbc.update("DELETE FROM platform.users WHERE id = ?", id);
        }
    }
    HttpResponse<String> send(String method, String path, String cookie, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("X-CloudSQL-Request", "1").header("Content-Type", "application/json");
        if (cookie != null) request.header("Cookie", cookie);
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    String checkout(String id, boolean failure) { return "{\"requestId\":\"" + id + "\",\"simulateFailure\":" + failure + "}"; }
    @Test void demoCheckoutEnforcesQuotasIsolationDeclineAndIdempotency() throws Exception {
        assertThat(send("GET", "/api/billing", null, null).statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/billing/upgrade", admin, checkout(UUID.randomUUID().toString(), false)).statusCode()).isEqualTo(403);
        for (int i = 0; i < 2; i++) assertThat(send("POST", "/api/databases", learner, "{\"name\":\"Original " + i + "\"}").statusCode()).isEqualTo(201);
        assertThat(send("POST", "/api/databases", learner, "{\"name\":\"Over quota\"}").statusCode()).isEqualTo(409);
        String declined = UUID.randomUUID().toString();
        assertThat(send("POST", "/api/billing/upgrade", learner, checkout(declined, true)).body()).contains("FREE", "DECLINED");
        assertThat(send("POST", "/api/billing/upgrade", learner, checkout(declined, false)).body()).contains("FREE");
        assertThat(send("POST", "/api/billing/upgrade", learner, "{\"requestId\":\"invalid\"}").statusCode()).isEqualTo(400);
        String receipt = UUID.randomUUID().toString();
        String payload = checkout(receipt, false);
        var successful = send("POST", "/api/billing/upgrade", learner, payload);
        assertThat(successful.statusCode()).as(successful.body()).isEqualTo(200);
        assertThat(successful.body()).contains("PRO", "52428800", "19900", "SUCCESS", "\"demoOnly\":true");
        send("POST", "/api/billing/upgrade", learner, payload);
        send("POST", "/api/billing/upgrade", learner, checkout(UUID.randomUUID().toString(), false));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform.demo_payments WHERE user_id = ? AND status = 'SUCCESS'", Integer.class, owner)).isEqualTo(1);
        assertThat(catalog.findByOwner(owner)).allMatch(w -> w.storageLimitBytes() == 50L * 1024 * 1024);
        for (int i = 2; i < 5; i++) assertThat(send("POST", "/api/databases", learner, "{\"name\":\"Pro " + i + "\"}").statusCode()).isEqualTo(201);
        assertThat(send("POST", "/api/databases", learner, "{\"name\":\"Still limited\"}").statusCode()).isEqualTo(409);
        assertThat(send("GET", "/api/billing", other, null).body()).contains("FREE").doesNotContain(receipt);
        assertThat(send("GET", "/api/databases", learner, null).body()).contains("\"maximumDatabases\":5", "262144000");
    }
    @Test void leaderboardUsesEarnedPointsExcludesAdminsAndNeverExposesPrivateData() throws Exception {
        jdbc.update("UPDATE platform.users SET name = ? WHERE id = ?", "Leader One", owner);
        jdbc.update("INSERT INTO platform.user_problem_progress (user_id, problem_id, status, attempts, points) VALUES (?, 1, 'SOLVED', 3, 9000)", owner);
        jdbc.update("INSERT INTO platform.user_problem_progress (user_id, problem_id, status, attempts, points) VALUES (?, 1, 'SOLVED', 1, 8000)", user(other));
        jdbc.update("INSERT INTO platform.user_problem_progress (user_id, problem_id, status, attempts, points) VALUES (?, 1, 'SOLVED', 1, 99999)", user(admin));
        var response = send("GET", "/api/leaderboard", learner, null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        var board = json.readTree(response.body());
        assertThat(board.get("yourRank").get("rank").asLong()).isEqualTo(1);
        assertThat(board.get("yourRank").get("points").asLong()).isEqualTo(9000);
        assertThat(board.get("leaders").get(0).get("reward").asString()).contains("Gold trophy");
        assertThat(response.body()).contains("Leader One", "Silver trophy").doesNotContain("99999", "@example", owner, TestAccounts.PASSWORD, "password_hash");
        assertThat(send("GET", "/api/leaderboard", admin, null).statusCode()).isEqualTo(403);
        assertThat(send("GET", "/api/leaderboard", null, null).statusCode()).isEqualTo(401);
    }
}

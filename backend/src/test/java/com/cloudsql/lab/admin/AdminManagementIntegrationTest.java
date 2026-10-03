package com.cloudsql.lab.admin;

import com.cloudsql.lab.TestAccounts;
import com.cloudsql.lab.auth.*;
import com.cloudsql.lab.problem.ProblemRepository;
import com.cloudsql.lab.config.ProblemDatabaseRegistry;
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
class AdminManagementIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired PasswordHasher passwords;
    @Autowired ProblemRepository problems;
    @Autowired ProblemDatabaseRegistry databases;
    String adminCookie, learnerCookie, adminId, learnerId;
    final List<String> createdUsers = new ArrayList<>();
    final HttpClient client = HttpClient.newHttpClient();
    final tools.jackson.databind.json.JsonMapper json = tools.jackson.databind.json.JsonMapper.builder().build();
    @BeforeEach void setup() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        new AdminProvisioner(jdbc, passwords, email, TestAccounts.PASSWORD).run(null);
        adminCookie = TestAccounts.login(port, email); learnerCookie = TestAccounts.signup(port);
        adminId = account(adminCookie).id(); learnerId = account(learnerCookie).id();
    }
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM platform.custom_problems WHERE created_by = ?", adminId);
        createdUsers.add(adminId); createdUsers.add(learnerId);
        for (String id : createdUsers) {
            jdbc.update("DELETE FROM platform.user_problem_progress WHERE user_id = ?", id);
            jdbc.update("DELETE FROM platform.users WHERE id = ?", id);
        }
    }
    Account account(String cookie) { return auth.resolve(cookie.substring(cookie.indexOf('=') + 1)); }
    HttpResponse<String> send(String method, String path, String cookie, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("X-CloudSQL-Request", "1").header("Content-Type", "application/json");
        if (cookie != null) builder.header("Cookie", cookie);
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    String question(String solution) {
        return json.writeValueAsString(Map.of("title", "Harbor Delivery Windows", "difficulty", "Easy", "category", "Basic Select", "description", "Return the name of deliveries requiring more than 3 slots. Sort by id.", "starterQuery", "SELECT * FROM deliveries", "solutionQuery", solution,
            "tables", List.of(Map.of("name", "deliveries", "columns", List.of(Map.of("name", "id", "type", "INTEGER"), Map.of("name", "name", "type", "VARCHAR"), Map.of("name", "slots", "type", "INTEGER")), "sampleRows", List.of(List.of(1, "Harbor A", 2), List.of(2, "Quay's cargo", 5))))));
    }
    @Test void adminCannotUseLearnerApisAndUserCannotManageAccountsOrQuestions() throws Exception {
        for (String path : List.of("/api/dashboard", "/api/problems", "/api/problems/1", "/api/databases")) assertThat(send("GET", path, adminCookie, null).statusCode()).isEqualTo(403);
        assertThat(send("POST", "/api/problems/1/submit", adminCookie, "{\"query\":\"SELECT 1\"}").statusCode()).isEqualTo(403);
        for (String path : List.of("/api/admin/users", "/api/admin/problems")) {
            assertThat(send("POST", path, learnerCookie, "{}").statusCode()).isEqualTo(403);
            assertThat(send("POST", path, null, "{}").statusCode()).isEqualTo(401);
        }
    }
    @Test void creatingLearnerNeverReplacesAdminSessionAndCannotGrantAdmin() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        String input = json.writeValueAsString(Map.of("name", "Managed Learner", "email", email, "password", TestAccounts.PASSWORD, "role", "ADMIN"));
        var response = send("POST", "/api/admin/users", adminCookie, input);
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("set-cookie")).isEmpty();
        var user = auth.login(email, TestAccounts.PASSWORD); createdUsers.add(user.id());
        assertThat(user.role()).isEqualTo("USER");
        assertThat(account(adminCookie).role()).isEqualTo("ADMIN");
        assertThat(response.body()).doesNotContain("password", "hash");
        assertThat(send("POST", "/api/admin/users", adminCookie, input).statusCode()).isEqualTo(409);
    }
    @Test void publishedQuestionIsPersistentExecutablePrivateAndAwardsPointsOnce() throws Exception {
        String solution = "SELECT name FROM deliveries WHERE slots > 3 ORDER BY id";
        var response = send("POST", "/api/admin/problems", adminCookie, question(solution));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        long id = json.readTree(response.body()).get("id").asLong();
        assertThat(new ProblemRepository(jdbc).findById(id)).isPresent();
        // A fresh registry loads persisted structured data, as after an application restart.
        var restarted = new com.cloudsql.lab.config.H2ProblemDatabaseRegistry(new ProblemRepository(jdbc));
        try (var connection = restarted.getConnection(id); var stmt = connection.createStatement(); var result = stmt.executeQuery(solution)) { assertThat(result.next()).isTrue(); assertThat(result.getString(1)).isEqualTo("Quay's cargo"); }
        var detail = send("GET", "/api/problems/" + id, learnerCookie, null);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.body()).doesNotContain("solutionQuery", solution);
        assertThat(send("GET", "/api/problems", learnerCookie, null).body()).contains("Harbor Delivery Windows");
        String query = json.writeValueAsString(Map.of("query", solution));
        assertThat(send("POST", "/api/problems/" + id + "/submit", learnerCookie, query).body()).contains("\"correct\":true", "\"pointsAwarded\":10");
        assertThat(send("POST", "/api/problems/" + id + "/submit", learnerCookie, query).body()).contains("\"pointsAwarded\":0");
        assertThat(send("GET", "/api/admin/analytics", adminCookie, null).body()).contains("Harbor Delivery Windows");
    }
    @Test void rejectsUnsafeOrBrokenQuestionsWithoutPublishing() throws Exception {
        int before = problems.findAll().size();
        for (String sql : List.of("DELETE FROM deliveries", "SELECT * FROM platform.users", "SELECT * FROM missing_table", "SELECT pg_sleep(1)")) assertThat(send("POST", "/api/admin/problems", adminCookie, question(sql)).statusCode()).isEqualTo(400);
        assertThat(send("POST", "/api/admin/problems", adminCookie, question("SELECT name FROM deliveries").replace("\"deliveries\"", "\"x); DROP TABLE platform.users;--\"")).statusCode()).isEqualTo(400);
        assertThat(send("POST", "/api/admin/problems", adminCookie, question("SELECT name FROM deliveries").replace("\"INTEGER\"", "\"INVALID\"")).statusCode()).isEqualTo(400);
        assertThat(problems.findAll()).hasSize(before);
    }
    @Test void structuredDatasetPreservesDecimalDatesBooleansAndNulls() throws Exception {
        String query = "SELECT amount, delivery_date, priority, note FROM cargo";
        String input = json.writeValueAsString(Map.of("title", "Cargo Schedule", "difficulty", "Medium", "category", "Basic Select", "description", "Return the cargo schedule fields.", "starterQuery", query, "solutionQuery", query,
            "tables", List.of(Map.of("name", "cargo", "columns", List.of(Map.of("name", "amount", "type", "DECIMAL"), Map.of("name", "delivery_date", "type", "DATE"), Map.of("name", "priority", "type", "BOOLEAN"), Map.of("name", "note", "type", "VARCHAR")), "sampleRows", List.of(Arrays.asList("14000000000000.1250", "2026-10-01", true, null))))));
        var response = send("POST", "/api/admin/problems", adminCookie, input);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        long id = json.readTree(response.body()).get("id").asLong();
        try (var connection = databases.getConnection(id); var statement = connection.createStatement(); var result = statement.executeQuery(query)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getBigDecimal(1)).isEqualByComparingTo("14000000000000.1250");
            assertThat(result.getDate(2).toString()).isEqualTo("2026-10-01");
            assertThat(result.getBoolean(3)).isTrue(); assertThat(result.getObject(4)).isNull();
        }
    }
}

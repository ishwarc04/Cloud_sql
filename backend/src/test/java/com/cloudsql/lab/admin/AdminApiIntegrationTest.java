package com.cloudsql.lab.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminApiIntegrationTest {
    private static final Pattern ID = Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"");
    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void returnsRealOverviewAndSafeReadOnlyWorkspaceData() throws Exception {
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        HttpResponse<String> initial = get("/api/admin/overview");
        assertThat(initial.statusCode()).isEqualTo(200);
        var baseline = json.readTree(initial.body());
        HttpResponse<String> created = post("/api/databases", "{\"name\":\"Admin Visibility Test\"}");
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        String databaseId = idFrom(created.body());
        assertThat(post("/api/problems/1/submit", "{\"query\":\"SELECT 1 AS wrong_answer\"}").statusCode()).isEqualTo(200);

        HttpResponse<String> overview = get("/api/admin/overview");
        assertThat(overview.statusCode()).isEqualTo(200);
        var totals = json.readTree(overview.body());
        assertThat(totals.get("totalDatabaseWorkspaces").asLong()).isEqualTo(baseline.get("totalDatabaseWorkspaces").asLong() + 1);
        assertThat(totals.get("activeDatabaseWorkspaces").asLong()).isEqualTo(baseline.get("activeDatabaseWorkspaces").asLong() + 1);
        assertThat(totals.get("totalPracticeAttempts").asLong()).isEqualTo(baseline.get("totalPracticeAttempts").asLong() + 1);
        assertThat(totals.get("totalPracticeProblems").asLong()).isEqualTo(baseline.get("totalPracticeProblems").asLong());
        assertThat(totals.get("serviceStatus").asString()).isEqualTo("OPERATIONAL");

        HttpResponse<String> databases = get("/api/admin/databases");
        assertThat(databases.statusCode()).isEqualTo(200);
        assertThat(databases.body()).contains("Admin Visibility Test", auth.resolve(learnerCookie.substring(learnerCookie.indexOf('=') + 1)).id(), databaseId,
                "storageUsedBytes", "storageLimitBytes");
        assertThat(databases.body()).doesNotContain("internalWorkspaceId", "jdbc:h2", "data/workspaces", "password", "connection");

        assertThat(delete("/api/databases/" + databaseId).statusCode()).isEqualTo(204);
    }

    private String idFrom(String body) {
        Matcher matcher = ID.matcher(body);
        assertThat(matcher.find()).as(body).isTrue();
        return matcher.group(1);
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return client.send(requestBuilder(URI.create(baseUrl() + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path) throws IOException, InterruptedException {
        return client.send(requestBuilder(URI.create(baseUrl() + path)).DELETE().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
        HttpRequest request = requestBuilder(URI.create(baseUrl() + path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String cookie;
    private String learnerCookie;
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired private com.cloudsql.lab.auth.PasswordHasher passwords;
    @org.springframework.beans.factory.annotation.Autowired private com.cloudsql.lab.auth.AuthService auth;
    @org.junit.jupiter.api.BeforeEach
    void signIn() throws Exception {
        String email = java.util.UUID.randomUUID() + "@example.test";
        new com.cloudsql.lab.auth.AdminProvisioner(jdbc, passwords, email, com.cloudsql.lab.TestAccounts.PASSWORD).run(null);
        cookie = com.cloudsql.lab.TestAccounts.login(port, email);
        learnerCookie = com.cloudsql.lab.TestAccounts.signup(port);
    }
    @org.junit.jupiter.api.AfterEach
    void removeTestAccounts() {
        for (String token : java.util.List.of(cookie, learnerCookie)) {
            String accountId = auth.resolve(token.substring(token.indexOf('=') + 1)).id();
            for (var workspace : catalog.findByOwner(accountId)) {
                engine.delete(workspace);
                catalog.delete(workspace.id());
            }
            jdbc.update("DELETE FROM platform.user_problem_progress WHERE user_id = ?", accountId);
            jdbc.update("DELETE FROM platform.users WHERE id = ?", accountId);
        }
    }
    @org.springframework.beans.factory.annotation.Autowired private com.cloudsql.lab.database.WorkspaceCatalogRepository catalog;
    @org.springframework.beans.factory.annotation.Autowired private com.cloudsql.lab.database.WorkspaceEngine engine;
    private HttpRequest.Builder requestBuilder(URI uri) {
        return HttpRequest.newBuilder(uri).header("Cookie", uri.getPath().startsWith("/api/admin") ? cookie : learnerCookie).header("X-CloudSQL-Request", "1");
    }
    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }
}

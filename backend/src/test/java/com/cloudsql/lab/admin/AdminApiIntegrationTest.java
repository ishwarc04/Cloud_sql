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
        HttpResponse<String> created = post("/api/databases", "{\"name\":\"Admin Visibility Test\"}");
        String databaseId = idFrom(created.body());
        post("/api/problems/1/submit", "{\"query\":\"SELECT 1 AS wrong_answer\"}");

        HttpResponse<String> overview = get("/api/admin/overview");
        assertThat(overview.statusCode()).isEqualTo(200);
        assertThat(overview.body()).contains(
                "\"totalDatabaseWorkspaces\":1", "\"activeDatabaseWorkspaces\":1",
                "\"totalPracticeProblems\":15", "\"totalPracticeAttempts\":1",
                "\"serviceStatus\":\"OPERATIONAL\"");

        HttpResponse<String> databases = get("/api/admin/databases");
        assertThat(databases.statusCode()).isEqualTo(200);
        assertThat(databases.body()).contains("Admin Visibility Test", auth.resolve(cookie.substring(cookie.indexOf('=') + 1)).id(), databaseId,
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
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired private com.cloudsql.lab.auth.PasswordHasher passwords;
    @org.springframework.beans.factory.annotation.Autowired private com.cloudsql.lab.auth.AuthService auth;
    @org.junit.jupiter.api.BeforeEach
    void signIn() throws Exception {
        String email = java.util.UUID.randomUUID() + "@example.test";
        new com.cloudsql.lab.auth.AdminProvisioner(jdbc, passwords, email, com.cloudsql.lab.TestAccounts.PASSWORD).run(null);
        cookie = com.cloudsql.lab.TestAccounts.login(port, email);
    }
    private HttpRequest.Builder requestBuilder(URI uri) {
        return HttpRequest.newBuilder(uri).header("Cookie", cookie).header("X-CloudSQL-Request", "1");
    }
    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }
}

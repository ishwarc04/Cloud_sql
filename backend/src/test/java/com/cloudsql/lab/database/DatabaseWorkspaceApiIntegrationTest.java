package com.cloudsql.lab.database;

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
class DatabaseWorkspaceApiIntegrationTest {
    private static final Pattern ID = Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"");
    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void supportsLifecycleSqlQuotaSchemaAndIsolation() throws Exception {
        HttpResponse<String> firstCreate = post("/api/databases", "{\"name\":\"Integration One\"}");
        HttpResponse<String> secondCreate = post("/api/databases", "{\"name\":\"Integration Two\"}");
        String firstId = idFrom(firstCreate.body());
        String secondId = idFrom(secondCreate.body());

        assertThat(firstCreate.statusCode()).isEqualTo(201);
        assertThat(secondCreate.statusCode()).isEqualTo(201);
        assertThat(get("/api/databases").body()).contains("Integration One", "Integration Two", "\"maximumDatabases\":2");
        assertThat(post("/api/databases", "{\"name\":\"Over Quota\"}").statusCode()).isEqualTo(409);

        assertSuccess(firstId, "CREATE TABLE customers (id INT PRIMARY KEY, name VARCHAR(80), active BOOLEAN)", "CREATE");
        assertSuccess(firstId, "INSERT INTO customers VALUES (1, 'Ada', TRUE)", "\"affectedRows\":1");
        assertSuccess(firstId, "INSERT INTO customers VALUES (2, 'Linus', TRUE)", "\"affectedRows\":1");
        assertSuccess(firstId, "UPDATE customers SET active = FALSE WHERE id = 2", "\"affectedRows\":1");
        HttpResponse<String> selected = execute(firstId, "SELECT id, name, active FROM customers ORDER BY id");
        assertThat(selected.statusCode()).isEqualTo(200);
        assertThat(selected.body()).contains("Ada", "Linus", "columns", "rows");
        assertSuccess(firstId, "DELETE FROM customers WHERE id = 2", "\"affectedRows\":1");

        HttpResponse<String> schema = get("/api/databases/" + firstId + "/schema");
        assertThat(schema.statusCode()).isEqualTo(200);
        assertThat(schema.body()).contains("customers", "id", "INTEGER", "name", "CHARACTER VARYING");

        assertRejected(firstId, "SELECT * FROM database_workspaces", "platform");
        assertRejected(firstId, "SELECT * FROM practice_1.employees", "practice");
        assertRejected(firstId, "SELECT * FROM workspace_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.customers", "platform");
        assertRejected(firstId, "SELECT * FROM platform.database_workspaces", "platform");
        assertRejected(firstId, "SELECT 1; SELECT 2", "Only one SQL statement");
        assertRejected(firstId, "CREATE SCHEMA unsafe", "table-level");
        assertRejected(firstId, "SHUTDOWN", "not allowed");
        assertThat(execute(secondId, "SELECT * FROM customers").statusCode()).isEqualTo(400);

        assertSuccess(firstId, "DROP TABLE customers", "DROP");
        assertThat(delete("/api/databases/" + firstId).statusCode()).isEqualTo(204);
        assertThat(get("/api/databases/" + firstId).statusCode()).isEqualTo(404);
        assertThat(delete("/api/databases/" + secondId).statusCode()).isEqualTo(204);
    }

    private void assertSuccess(String id, String sql, String expected) throws Exception {
        HttpResponse<String> response = execute(id, sql);
        assertThat(response.statusCode()).as(sql + " -> " + response.body()).isEqualTo(200);
        assertThat(response.body()).contains(expected);
    }

    private void assertRejected(String id, String sql, String expected) throws Exception {
        HttpResponse<String> response = execute(id, sql);
        assertThat(response.statusCode()).as(sql).isEqualTo(400);
        assertThat(response.body()).containsIgnoringCase(expected);
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

    private HttpResponse<String> execute(String id, String query) throws IOException, InterruptedException {
        return post("/api/databases/" + id + "/execute", "{\"query\":\"" + query.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}");
    }

    private HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
        HttpRequest request = requestBuilder(URI.create(baseUrl() + path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String cookie;
    @org.junit.jupiter.api.BeforeEach
    void signIn() throws Exception {
        cookie = com.cloudsql.lab.TestAccounts.signup(port);
    }
    private HttpRequest.Builder requestBuilder(URI uri) {
        return HttpRequest.newBuilder(uri).header("Cookie", cookie).header("X-CloudSQL-Request", "1");
    }
    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }
}

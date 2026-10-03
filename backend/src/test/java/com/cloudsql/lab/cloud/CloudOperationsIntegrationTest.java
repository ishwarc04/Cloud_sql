package com.cloudsql.lab.cloud;

import com.cloudsql.lab.TestAccounts;
import com.cloudsql.lab.auth.*;
import com.cloudsql.lab.database.*;
import java.net.URI;
import java.net.http.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CloudOperationsIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired PasswordHasher passwords;
    @Autowired CloudOperationsService operations;
    @Autowired WorkspaceCatalogRepository catalog;
    @Autowired WorkspaceEngine engine;
    String adminCookie, cookie, otherCookie, owner;
    List<String> ids;
    final HttpClient client = HttpClient.newHttpClient();
    final tools.jackson.databind.json.JsonMapper json = tools.jackson.databind.json.JsonMapper.builder().build();
    @BeforeEach void setup() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        new AdminProvisioner(jdbc, passwords, email, TestAccounts.PASSWORD).run(null);
        adminCookie = TestAccounts.login(port, email); cookie = TestAccounts.signup(port); otherCookie = TestAccounts.signup(port);
        owner = account(cookie); ids = List.of(account(adminCookie), owner, account(otherCookie));
    }
    @AfterEach void cleanup() {
        for (String id : ids) {
            for (var workspace : catalog.findByOwner(id)) { engine.delete(workspace); catalog.delete(workspace.id()); }
            jdbc.update("DELETE FROM platform.user_problem_progress WHERE user_id = ?", id);
            jdbc.update("DELETE FROM platform.users WHERE id = ?", id);
        }
    }
    String account(String token) { return auth.resolve(token.substring(token.indexOf('=') + 1)).id(); }
    HttpRequest request(String method, String path, String token, String body) {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("X-CloudSQL-Request", "1").header("Content-Type", "application/json");
        if (token != null) builder.header("Cookie", token);
        return builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build();
    }
    HttpResponse<String> send(String method, String path, String token, String body) throws Exception { return client.send(request(method, path, token, body), HttpResponse.BodyHandlers.ofString()); }
    String id(HttpResponse<String> response) { assertThat(response.statusCode()).as(response.body()).isEqualTo(201); return json.readTree(response.body()).get("id").asString(); }
    void sql(String database, String query) throws Exception { var response = send("POST", "/api/databases/" + database + "/execute", cookie, json.writeValueAsString(Map.of("query", query))); assertThat(response.statusCode()).as(response.body()).isEqualTo(200); }

    @Test void cloudEndpointsAreAdminOnlyAndAuditDoesNotExposeCredentialsOrQueries() throws Exception {
        assertThat(send("GET", "/api/admin/cloud", null, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/admin/cloud", cookie, null).statusCode()).isEqualTo(403);
        assertThat(send("POST", "/api/admin/cloud/probe", cookie, null).statusCode()).isEqualTo(403);
        String database = id(send("POST", "/api/databases", cookie, "{\"name\":\"Audit Test\"}"));
        sql(database, "SELECT 'private-query-marker' AS payload");
        var response = send("POST", "/api/admin/cloud/probe", adminCookie, null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).contains("WORKSPACE_CREATED", "SQL_SELECT", "ACCESS_DENIED", "probeSuccessPercent", "averageLatencyMs");
        assertThat(response.body()).doesNotContain(TestAccounts.PASSWORD, "private-query-marker", "password_hash", "cloudsql_session", "internal_workspace_id", "jdbc:");
        assertThat(operations.report().hours()).hasSize(24);
        assertThat(operations.report().health()).isNotEmpty();
    }
    @Test void concurrentWorkspaceCreationCannotBypassAccountQuota() throws Exception {
        var futures = java.util.stream.IntStream.range(0, 6).mapToObj(i -> client.sendAsync(request("POST", "/api/databases", cookie, "{\"name\":\"Parallel " + i + "\"}"), HttpResponse.BodyHandlers.ofString())).toList();
        var statuses = futures.stream().map(f -> f.join().statusCode()).toList();
        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(2);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(4);
        assertThat(catalog.findByOwner(owner)).hasSize(2);
    }
    @Test void snapshotRoundTripPreservesRowsTypesPrimaryKeyAndSurvivesSourceDeletion() throws Exception {
        String database = id(send("POST", "/api/databases", cookie, "{\"name\":\"Recovery Source\"}"));
        sql(database, "CREATE TABLE cargo (id INTEGER PRIMARY KEY, amount DECIMAL(12,4), delivery_date DATE, active BOOLEAN, note VARCHAR(100))");
        sql(database, "INSERT INTO cargo VALUES (1, 12.3456, '2026-10-03', TRUE, 'Quay''s load')");
        sql(database, "INSERT INTO cargo VALUES (2, NULL, NULL, FALSE, NULL)");
        String backup = id(send("POST", "/api/databases/" + database + "/backups", cookie, null));
        assertThat(send("GET", "/api/backups/" + backup, otherCookie, null).statusCode()).isEqualTo(404);
        assertThat(send("POST", "/api/backups/" + backup + "/restore", otherCookie, "{\"name\":\"Stolen Copy\"}").statusCode()).isEqualTo(404);
        assertThat(send("GET", "/api/backups/" + backup, adminCookie, null).statusCode()).isEqualTo(403);
        assertThat(send("GET", "/api/backups/" + backup, cookie, null).body()).contains("12.3456", "2026-10-03", "primaryKey", "Quay's load").doesNotContain("internalWorkspaceId", "password");
        assertThat(send("DELETE", "/api/databases/" + database, cookie, null).statusCode()).isEqualTo(204);
        String restored = id(send("POST", "/api/backups/" + backup + "/restore", cookie, "{\"name\":\"Recovered Cargo\"}"));
        var result = send("POST", "/api/databases/" + restored + "/execute", cookie, "{\"query\":\"SELECT * FROM cargo ORDER BY id\"}");
        assertThat(result.statusCode()).as(result.body()).isEqualTo(200);
        assertThat(result.body()).contains("12.3456", "2026-10-03", "Quay's load", "null");
        assertThat(send("POST", "/api/databases/" + restored + "/execute", cookie, "{\"query\":\"INSERT INTO cargo (id) VALUES (1)\"}").statusCode()).isEqualTo(400);
        assertThat(send("GET", "/api/admin/cloud", adminCookie, null).body()).contains("BACKUP_CREATED", "BACKUP_RESTORED", "BACKUP_DOWNLOADED");
        assertThat(send("DELETE", "/api/backups/" + backup, cookie, null).statusCode()).isEqualTo(204);
        assertThat(send("GET", "/api/backups/" + backup, cookie, null).statusCode()).isEqualTo(404);
    }
    @Test void backupAndRestoreQuotasAreEnforcedAndUnsupportedSchemaIsRejected() throws Exception {
        String first = id(send("POST", "/api/databases", cookie, "{\"name\":\"Backup Quotas\"}"));
        String backup = id(send("POST", "/api/databases/" + first + "/backups", cookie, null));
        id(send("POST", "/api/databases/" + first + "/backups", cookie, null)); id(send("POST", "/api/databases/" + first + "/backups", cookie, null));
        assertThat(send("POST", "/api/databases/" + first + "/backups", cookie, null).statusCode()).isEqualTo(409);
        id(send("POST", "/api/databases", cookie, "{\"name\":\"Full Account\"}"));
        assertThat(send("POST", "/api/backups/" + backup + "/restore", cookie, "{\"name\":\"Too Many\"}").statusCode()).isEqualTo(409);
        assertThat(catalog.findByOwner(owner)).hasSize(2);
        assertThat(send("DELETE", "/api/backups/" + backup, otherCookie, null).statusCode()).isEqualTo(404);
        assertThat(send("DELETE", "/api/backups/" + backup, cookie, null).statusCode()).isEqualTo(204);
        sql(first, "CREATE TABLE unsupported (id INTEGER DEFAULT 5)");
        assertThat(send("POST", "/api/databases/" + first + "/backups", cookie, null).statusCode()).isEqualTo(400);
    }
    @Test void concurrentMetricsAggregateAndRetentionRemovesOldData() {
        long before = jdbc.queryForObject("SELECT COALESCE(SUM(requests),0) FROM platform.request_metrics WHERE user_id = ? AND route_group = 'BACKUP'", Long.class, owner);
        java.util.stream.IntStream.range(0, 20).parallel().forEach(i -> operations.recordRequest(owner, "BACKUP", i % 2 == 0 ? 200 : 500, 10));
        var usage = operations.report().usage().stream().filter(u -> u.userId().equals(owner)).findFirst().orElseThrow();
        assertThat(usage.requests()).isGreaterThanOrEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT SUM(requests) FROM platform.request_metrics WHERE user_id = ? AND route_group = 'BACKUP'", Long.class, owner)).isEqualTo(before + 20);
        Timestamp old = Timestamp.from(Instant.now().minusSeconds(9 * 86400));
        jdbc.update("INSERT INTO platform.health_samples VALUES (?, ?, 'UP', 1)", "old-health", old);
        jdbc.update("INSERT INTO platform.request_metrics VALUES (?, 'BACKUP', ?, 1, 0, 1, 1)", old, owner);
        operations.probe();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform.health_samples WHERE id = 'old-health'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform.request_metrics WHERE bucket_start = ?", Integer.class, old)).isZero();
    }
}

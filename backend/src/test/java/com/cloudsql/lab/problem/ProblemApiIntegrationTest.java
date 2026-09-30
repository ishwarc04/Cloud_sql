package com.cloudsql.lab.problem;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProblemApiIntegrationTest {
    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void listsSeededProblems() throws Exception {
        HttpResponse<String> response = get("/api/problems");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Second Highest Salary", "Customers Without Orders", "Department Salary Report");
    }

    @Test
    void returnsOneProblemWithSchema() throws Exception {
        HttpResponse<String> response = get("/api/problems/2");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Customers Without Orders", "customers", "orders", "starterQuery");
    }

    @Test
    void executesAValidSelect() throws Exception {
        HttpResponse<String> response = execute(2, "SELECT id, name FROM customers ORDER BY id");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("columns", "rows", "Northstar Labs", "executionTimeMs");
        assertThat(response.body()).contains("\"error\":null");
    }

    @Test
    void returnsControlledSqlError() throws Exception {
        HttpResponse<String> response = execute(1, "SELECT missing_column FROM employees");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"columns\":[]", "\"rows\":[]", "\"error\":");
    }

    @Test
    void rejectsEveryMutationKeyword() throws Exception {
        String[] queries = {
                "INSERT INTO employees VALUES (99, 'X', 'Y', 1)",
                "UPDATE employees SET salary = 1",
                "DELETE FROM employees",
                "DROP TABLE employees",
                "ALTER TABLE employees ADD COLUMN unsafe INT",
                "CREATE TABLE unsafe (id INT)",
                "TRUNCATE TABLE employees"
        };

        for (String query : queries) {
            HttpResponse<String> response = execute(1, query);
            assertThat(response.statusCode()).as(query).isEqualTo(400);
            assertThat(response.body()).contains("Only SELECT statements are allowed");
        }
    }

    @Test
    void rejectsMultipleStatements() throws Exception {
        HttpResponse<String> response = execute(1, "SELECT * FROM employees; SELECT * FROM employees");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Only one SQL statement");
    }

    @Test
    void problemDatabasesAreIsolated() throws Exception {
        HttpResponse<String> firstProblem = execute(1, "SELECT COUNT(*) AS count FROM employees");
        HttpResponse<String> thirdProblem = execute(3, "SELECT COUNT(*) AS count FROM employees");
        HttpResponse<String> unavailableTable = execute(1, "SELECT * FROM customers");

        assertThat(firstProblem.body()).contains("5");
        assertThat(thirdProblem.body()).contains("6");
        assertThat(unavailableTable.statusCode()).isEqualTo(400);

        HttpResponse<String> otherSchema = execute(1, "SELECT * FROM practice_problem_2.customers");
        HttpResponse<String> platformSchema = execute(1, "SELECT * FROM platform.database_workspaces");
        assertThat(otherSchema.statusCode()).isEqualTo(400);
        assertThat(platformSchema.statusCode()).isEqualTo(400);
    }

    @Test
    void judgesCorrectAndIncorrectSubmissionsAndExposesProgress() throws Exception {
        HttpResponse<String> incorrect = submit(2, "SELECT id, name FROM customers ORDER BY id");
        HttpResponse<String> attemptedList = get("/api/problems");
        HttpResponse<String> correct = submit(2, "SELECT c.id, c.name FROM customers c LEFT JOIN orders o ON o.customer_id = c.id WHERE o.id IS NULL ORDER BY c.id");
        HttpResponse<String> solvedDetail = get("/api/problems/2");

        assertThat(incorrect.statusCode()).isEqualTo(200);
        assertThat(incorrect.body()).contains("\"correct\":false", "\"status\":\"ATTEMPTED\"", "\"attempts\":1");
        assertThat(attemptedList.body()).contains("\"status\":\"ATTEMPTED\"");
        assertThat(correct.statusCode()).isEqualTo(200);
        assertThat(correct.body()).contains("\"correct\":true", "\"status\":\"SOLVED\"", "\"attempts\":2");
        assertThat(solvedDetail.body()).contains("\"status\":\"SOLVED\"", "\"attempts\":2");
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> execute(long problemId, String query) throws IOException, InterruptedException {
        return postQuery(problemId, "execute", query);
    }

    private HttpResponse<String> submit(long problemId, String query) throws IOException, InterruptedException {
        return postQuery(problemId, "submit", query);
    }

    private HttpResponse<String> postQuery(long problemId, String action, String query) throws IOException, InterruptedException {
        String escapedQuery = query.replace("\\", "\\\\").replace("\"", "\\\"");
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/problems/" + problemId + "/" + action))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"" + escapedQuery + "\"}"))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }
}

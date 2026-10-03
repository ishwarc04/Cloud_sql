package com.cloudsql.lab.admin;

import com.cloudsql.lab.problem.*;
import com.cloudsql.lab.problem.model.*;
import jakarta.validation.constraints.*;
import java.sql.*;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataAccessException;

@Service
public class AdminQuestionService {
    public record CreateQuestion(@NotBlank @Size(max=120) String title,
            @NotBlank @Pattern(regexp="Easy|Medium|Hard") String difficulty,
            @NotBlank String category, @NotBlank @Size(max=8000) String description,
            @NotBlank @Size(max=20000) String starterQuery, @NotBlank @Size(max=20000) String solutionQuery,
            @NotNull List<TablePreview> tables) { }
    public record PublishedQuestion(long id, String title, String difficulty, String category) { }
    private final JdbcTemplate jdbc;
    private final QuerySafetyValidator validator;
    private final tools.jackson.databind.json.JsonMapper json = tools.jackson.databind.json.JsonMapper.builder().build();
    public AdminQuestionService(JdbcTemplate jdbc, QuerySafetyValidator validator) { this.jdbc = jdbc; this.validator = validator; }

    @Transactional
    public PublishedQuestion create(CreateQuestion input, String adminId) {
        if (!Set.of("Basic Select", "Advanced Select", "Aggregation", "Basic Join", "Advanced Join", "Alternative Queries").contains(input.category()))
            throw new QueryValidationException("Choose a supported SQL category.");
        StructuredProblemDataset.validate(input.tables());
        String starter = validator.validate(input.starterQuery()), solution = validator.validate(input.solutionQuery());
        try {
            return jdbc.execute((ConnectionCallback<PublishedQuestion>) connection -> {
                long id;
                try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT NEXTVAL('platform.custom_problem_ids')")) { result.next(); id = result.getLong(1); }
                String schema = "practice_problem_" + id;
                boolean postgres = connection.getMetaData().getDatabaseProductName().equals("PostgreSQL");
                String previousSchema = connection.getSchema();
                try {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("CREATE SCHEMA " + schema);
                        statement.execute(postgres ? "SET LOCAL search_path TO " + schema + ", pg_temp" : "SET SCHEMA " + schema);
                    }
                    StructuredProblemDataset.populate(connection, input.tables());
                    checkQuery(connection, starter); checkQuery(connection, solution);
                    String slug = input.title().trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-") + "-" + id;
                    var definition = new ProblemDefinition(id, slug, input.title().trim(), input.difficulty(), input.category(), input.description().trim(), starter, solution, null, input.tables());
                    try (PreparedStatement statement = connection.prepareStatement("INSERT INTO platform.custom_problems (id, definition, created_by, created_at) VALUES (?, ?, ?, ?)")) {
                        statement.setLong(1, id); statement.setString(2, json.writeValueAsString(definition)); statement.setString(3, adminId); statement.setTimestamp(4, Timestamp.from(java.time.Instant.now())); statement.executeUpdate();
                    }
                    return new PublishedQuestion(id, definition.title(), definition.difficulty(), definition.topic());
                } finally { if (!postgres) connection.setSchema(previousSchema); }
            });
        } catch (DataAccessException exception) { throw new QueryValidationException("Question could not be published. Check table names, sample values, and SQL queries."); }
    }
    private void checkQuery(Connection connection, String query) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(5); statement.setMaxRows(201);
            try (ResultSet result = statement.executeQuery(query)) {
                int count = 0;
                while (result.next()) if (++count > 200) throw new QueryValidationException("Queries must return at most 200 rows.");
            }
        }
    }
}

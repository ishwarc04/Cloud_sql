package com.cloudsql.lab.config;

import com.cloudsql.lab.database.WorkspaceProperties;
import com.cloudsql.lab.problem.ProblemRepository;
import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class PostgresProblemDatabaseRegistry implements ProblemDatabaseRegistry {
    private final DataSource dataSource;
    private final ProblemRepository problems;
    private final WorkspaceProperties properties;

    public PostgresProblemDatabaseRegistry(DataSource dataSource, ProblemRepository problems, WorkspaceProperties properties) {
        this.dataSource = dataSource;
        this.problems = problems;
        this.properties = properties;
    }

    @PostConstruct
    void initialize() {
        problems.findAll().forEach(problem -> {
            String schema = schemaName(problem.id());
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                connection.setAutoCommit(false);
                statement.execute("SELECT pg_advisory_xact_lock(" + (900000 + problem.id()) + ")");
                statement.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
                boolean seeded;
                try (var result = statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '" + schema + "'")) {
                    result.next(); seeded = result.getInt(1) > 0;
                }
                if (!seeded) {
                    statement.execute("SET LOCAL search_path TO " + schema + ", pg_temp");
                    if (problem.seedScript() == null) com.cloudsql.lab.problem.StructuredProblemDataset.populate(connection, problem.tables());
                    else new ResourceDatabasePopulator(new ClassPathResource(problem.seedScript())).populate(connection);
                }
                connection.commit();
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not initialize SQL Practice problem " + problem.id() + ".", exception);
            }
        });
    }

    @Override
    public Connection getConnection(long problemId) throws SQLException {
        if (problems.findById(problemId).isEmpty()) throw new IllegalArgumentException("No execution database exists for problem " + problemId + ".");
        Connection connection = dataSource.getConnection();
        try {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET LOCAL search_path TO " + schemaName(problemId) + ", pg_temp");
                statement.execute("SET LOCAL statement_timeout = '" + properties.queryTimeoutSeconds() + "s'");
            }
            return connection;
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
    }

    private String schemaName(long problemId) {
        return "practice_problem_" + problemId;
    }
}

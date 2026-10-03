package com.cloudsql.lab.config;

import com.cloudsql.lab.problem.ProblemRepository;
import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class H2ProblemDatabaseRegistry implements ProblemDatabaseRegistry {
    private final ProblemRepository problemRepository;
    private final Map<Long, DataSource> dataSources = new ConcurrentHashMap<>();

    public H2ProblemDatabaseRegistry(ProblemRepository problemRepository) {
        this.problemRepository = problemRepository;
    }

    @PostConstruct
    void initialize() {
        problemRepository.findAll().forEach(problem -> dataSources.put(problem.id(), createDatabase(problem)));
    }
    private DataSource createDatabase(com.cloudsql.lab.problem.model.ProblemDefinition problem) {
            String url = "jdbc:h2:mem:practice_" + problem.id() + "_" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";
            DataSource dataSource = new DriverManagerDataSource(url, "sa", "");
            if (problem.seedScript() == null) {
                try (Connection connection = dataSource.getConnection()) { com.cloudsql.lab.problem.StructuredProblemDataset.populate(connection, problem.tables()); }
                catch (SQLException exception) { throw new IllegalStateException("Could not initialize question dataset", exception); }
            } else new ResourceDatabasePopulator(new ClassPathResource(problem.seedScript())).execute(dataSource);
            return dataSource;
    }

    @Override
    public Connection getConnection(long problemId) throws SQLException {
        DataSource dataSource = dataSources.computeIfAbsent(problemId, id -> problemRepository.findById(id).map(this::createDatabase).orElse(null));
        if (dataSource == null) throw new IllegalArgumentException("No execution database exists for problem " + problemId + ".");
        return dataSource.getConnection();
    }
}

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
        problemRepository.findAll().forEach(problem -> {
            String url = "jdbc:h2:mem:practice_" + problem.id() + "_" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";
            DataSource dataSource = new DriverManagerDataSource(url, "sa", "");
            new ResourceDatabasePopulator(new ClassPathResource(problem.seedScript())).execute(dataSource);
            dataSources.put(problem.id(), dataSource);
        });
    }

    @Override
    public Connection getConnection(long problemId) throws SQLException {
        DataSource dataSource = dataSources.get(problemId);
        if (dataSource == null) throw new IllegalArgumentException("No execution database exists for problem " + problemId + ".");
        return dataSource.getConnection();
    }
}

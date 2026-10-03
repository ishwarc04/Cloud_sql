package com.cloudsql.lab.problem;

import com.cloudsql.lab.config.ProblemDatabaseRegistry;
import com.cloudsql.lab.problem.dto.ExecuteQueryResponse;
import com.cloudsql.lab.problem.dto.ProblemDetailResponse;
import com.cloudsql.lab.problem.dto.ProblemSummaryResponse;
import com.cloudsql.lab.problem.dto.SubmissionResponse;
import com.cloudsql.lab.problem.model.ProblemDefinition;
import com.cloudsql.lab.progress.CurrentUserProvider;
import com.cloudsql.lab.progress.ProblemProgress;
import com.cloudsql.lab.progress.ProgressRepository;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ProblemService {
    private static final int MAX_RESULT_ROWS = 200;
    private static final int QUERY_TIMEOUT_SECONDS = 5;

    private final ProblemRepository repository;
    private final ProblemDatabaseRegistry databases;
    private final QuerySafetyValidator validator;
    private final ProgressRepository progressRepository;
    private final CurrentUserProvider currentUserProvider;

    public ProblemService(ProblemRepository repository, ProblemDatabaseRegistry databases, QuerySafetyValidator validator,
            ProgressRepository progressRepository, CurrentUserProvider currentUserProvider) {
        this.repository = repository;
        this.databases = databases;
        this.validator = validator;
        this.progressRepository = progressRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public List<ProblemSummaryResponse> findAll() {
        Map<Long, ProblemProgress> progress = progressRepository.findByUserId(currentUserProvider.currentUserId());
        return repository.findAll().stream().map(problem -> toSummary(problem,
                progress.getOrDefault(problem.id(), ProblemProgress.notStarted(currentUserProvider.currentUserId(), problem.id())))).toList();
    }

    public ProblemDetailResponse findById(long id) {
        return toDetail(getProblem(id), progressRepository.find(currentUserProvider.currentUserId(), id));
    }

    public ExecuteQueryResponse execute(long id, String rawQuery) {
        getProblem(id);
        String query = validator.validate(rawQuery);
        return executeQuery(id, query);
    }

    public SubmissionResponse submit(long id, String rawQuery) {
        ProblemDefinition problem = getProblem(id);
        ExecuteQueryResponse candidate;
        try {
            String query = validator.validate(rawQuery);
            candidate = executeQuery(id, query);
        } catch (QueryValidationException | QueryExecutionException exception) {
            progressRepository.recordAttempt(currentUserProvider.currentUserId(), id, false, 0, rawQuery, "ERROR");
            throw exception;
        }
        ExecuteQueryResponse expected = executeQuery(id, problem.solutionQuery());
        boolean correct = resultsMatch(candidate, expected);
        var attempt = progressRepository.recordAttempt(currentUserProvider.currentUserId(), id, correct, pointsFor(problem.difficulty()), rawQuery, correct ? "ACCEPTED" : "WRONG_ANSWER");
        ProblemProgress progress = attempt.progress();
        String message = correct ? "Accepted — your result matches the expected output." : "Not accepted — compare your columns, values, and row order with the requested output.";
        return new SubmissionResponse(candidate.columns(), candidate.rows(), candidate.executionTimeMs(), null,
                correct, message, progress.status(), progress.attempts(), attempt.pointsAwarded());
    }

    private ExecuteQueryResponse executeQuery(long id, String query) {
        long startedAt = System.nanoTime();

        try (Connection connection = databases.getConnection(id); Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            statement.setMaxRows(MAX_RESULT_ROWS);

            try (ResultSet resultSet = statement.executeQuery(query)) {
                ResultSetMetaData metadata = resultSet.getMetaData();
                List<String> columns = new ArrayList<>();
                for (int index = 1; index <= metadata.getColumnCount(); index++) {
                    columns.add(metadata.getColumnLabel(index));
                }

                List<List<Object>> rows = new ArrayList<>();
                while (resultSet.next()) {
                    List<Object> row = new ArrayList<>();
                    for (int index = 1; index <= metadata.getColumnCount(); index++) {
                        row.add(com.cloudsql.lab.common.SqlCellValues.jsonValue(resultSet.getObject(index)));
                    }
                    rows.add(row);
                }

                return new ExecuteQueryResponse(columns, rows, elapsedMilliseconds(startedAt), null);
            }
        } catch (SQLException exception) {
            throw new QueryExecutionException(cleanSqlMessage(exception.getMessage()));
        }
    }

    public static int pointsFor(String difficulty) {
        return switch (difficulty) { case "Easy" -> 10; case "Medium" -> 20; case "Hard" -> 30; default -> throw new IllegalArgumentException("Unknown difficulty"); };
    }

    private ProblemDefinition getProblem(long id) {
        return repository.findById(id).orElseThrow(() -> new ProblemNotFoundException(id));
    }

    private ProblemSummaryResponse toSummary(ProblemDefinition problem, ProblemProgress progress) {
        return new ProblemSummaryResponse(problem.id(), problem.slug(), problem.title(), problem.difficulty(), problem.topic(), progress.status(), progress.attempts());
    }

    private ProblemDetailResponse toDetail(ProblemDefinition problem, ProblemProgress progress) {
        return new ProblemDetailResponse(problem.id(), problem.slug(), problem.title(), problem.difficulty(), problem.topic(), problem.description(), problem.starterQuery(), problem.tables(), progress.status(), progress.attempts());
    }

    private boolean resultsMatch(ExecuteQueryResponse candidate, ExecuteQueryResponse expected) {
        if (candidate.columns().size() != expected.columns().size() || candidate.rows().size() != expected.rows().size()) return false;
        for (int index = 0; index < expected.columns().size(); index++) {
            if (!candidate.columns().get(index).equalsIgnoreCase(expected.columns().get(index))) return false;
        }
        for (int rowIndex = 0; rowIndex < expected.rows().size(); rowIndex++) {
            List<Object> candidateRow = candidate.rows().get(rowIndex);
            List<Object> expectedRow = expected.rows().get(rowIndex);
            if (candidateRow.size() != expectedRow.size()) return false;
            for (int columnIndex = 0; columnIndex < expectedRow.size(); columnIndex++) {
                if (!normalizedValue(candidateRow.get(columnIndex)).equals(normalizedValue(expectedRow.get(columnIndex)))) return false;
            }
        }
        return true;
    }

    private String normalizedValue(Object value) {
        if (value == null) return "<NULL>";
        if (value instanceof Number) return new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
        return value.toString();
    }

    private long elapsedMilliseconds(long startedAt) {
        return Math.max(1, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private String cleanSqlMessage(String message) {
        if (message == null || message.isBlank()) return "The query could not be executed.";
        int detailStart = message.indexOf("; SQL statement:");
        return detailStart > 0 ? message.substring(0, detailStart) : message;
    }
}

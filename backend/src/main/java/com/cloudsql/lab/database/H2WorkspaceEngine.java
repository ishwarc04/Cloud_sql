package com.cloudsql.lab.database;

import com.cloudsql.lab.database.dto.WorkspaceExecutionResponse;
import com.cloudsql.lab.database.model.DatabaseWorkspace;
import com.cloudsql.lab.database.model.WorkspaceColumn;
import com.cloudsql.lab.database.model.WorkspaceTable;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

@Component
@Profile("test")
public class H2WorkspaceEngine implements WorkspaceEngine {
    private static final Pattern INTERNAL_ID = Pattern.compile("[0-9a-f]{32}");
    private final WorkspaceProperties properties;
    private Path storageDirectory;

    public H2WorkspaceEngine(WorkspaceProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initializeStorage() {
        try {
            storageDirectory = Path.of(properties.storageDirectory()).toAbsolutePath().normalize();
            Files.createDirectories(storageDirectory);
        } catch (IOException exception) {
            throw new WorkspaceException("Workspace storage directory could not be initialized.", exception);
        }
    }

    @Override
    public long create(DatabaseWorkspace workspace) {
        try (Connection ignored = DriverManager.getConnection(connectionUrl(workspace), "sa", "")) {
            return storageUsedBytes(workspace);
        } catch (SQLException exception) {
            throw sqlException(exception);
        }
    }

    @Override
    public WorkspaceExecutionResponse execute(DatabaseWorkspace workspace, String sql, String statementType) {
        if (!statementType.equals("SELECT") && storageUsedBytes(workspace) >= workspace.storageLimitBytes()) {
            throw new WorkspaceQuotaException("This database has reached its configured storage limit.");
        }

        long startedAt = System.nanoTime();
        try (Connection connection = DriverManager.getConnection(connectionUrl(workspace), "sa", "");
                Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(properties.queryTimeoutSeconds());
            statement.setMaxRows(properties.maxReturnedRows());
            boolean hasRows = statement.execute(sql);
            if (!hasRows) {
                return new WorkspaceExecutionResponse(List.of(), List.of(), Math.max(0, statement.getUpdateCount()),
                        statementType, elapsedMilliseconds(startedAt), null);
            }
            try (ResultSet resultSet = statement.getResultSet()) {
                return readRows(resultSet, statementType, startedAt);
            }
        } catch (SQLException exception) {
            throw sqlException(exception);
        }
    }

    @Override
    public List<WorkspaceTable> inspectSchema(DatabaseWorkspace workspace) {
        List<WorkspaceTable> tables = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(connectionUrl(workspace), "sa", "")) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet tableRows = metadata.getTables(null, null, "%", new String[] { "TABLE" })) {
                while (tableRows.next()) {
                    if (!"PUBLIC".equalsIgnoreCase(tableRows.getString("TABLE_SCHEM"))) continue;
                    String tableName = tableRows.getString("TABLE_NAME");
                    List<WorkspaceColumn> columns = new ArrayList<>();
                    try (ResultSet columnRows = metadata.getColumns(null, tableRows.getString("TABLE_SCHEM"), tableName, "%")) {
                        while (columnRows.next()) {
                            columns.add(new WorkspaceColumn(columnRows.getString("COLUMN_NAME"),
                                    columnRows.getString("TYPE_NAME"), columnRows.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls));
                        }
                    }
                    tables.add(new WorkspaceTable(tableName, columns));
                }
            }
        } catch (SQLException exception) {
            throw sqlException(exception);
        }
        tables.sort(Comparator.comparing(WorkspaceTable::name, String.CASE_INSENSITIVE_ORDER));
        return tables;
    }

    @Override
    public long storageUsedBytes(DatabaseWorkspace workspace) {
        long total = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(storageDirectory, workspace.internalWorkspaceId() + "*")) {
            for (Path file : files) if (Files.isRegularFile(file)) total += Files.size(file);
            return total;
        } catch (IOException exception) {
            throw new WorkspaceException("Database storage usage could not be measured.", exception);
        }
    }

    @Override
    public void delete(DatabaseWorkspace workspace) {
        try (Connection connection = DriverManager.getConnection(connectionUrl(workspace), "sa", "");
                Statement statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        } catch (SQLException ignored) {
            // H2 can report the expected closed-database state after SHUTDOWN.
        }

        IOException lastFailure = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                deleteWorkspaceFiles(workspace);
                return;
            } catch (IOException exception) {
                lastFailure = exception;
                try {
                    Thread.sleep(75L * (attempt + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw new WorkspaceException("Database files are still in use. Close active operations and try deletion again.", lastFailure);
    }

    private void deleteWorkspaceFiles(DatabaseWorkspace workspace) throws IOException {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(storageDirectory, workspace.internalWorkspaceId() + "*")) {
            for (Path file : files) Files.deleteIfExists(file);
        }
    }

    private WorkspaceExecutionResponse readRows(ResultSet resultSet, String statementType, long startedAt) throws SQLException {
        ResultSetMetaData metadata = resultSet.getMetaData();
        List<String> columns = new ArrayList<>();
        for (int index = 1; index <= metadata.getColumnCount(); index++) columns.add(metadata.getColumnLabel(index));
        List<List<Object>> rows = new ArrayList<>();
        while (resultSet.next()) {
            List<Object> row = new ArrayList<>();
            for (int index = 1; index <= metadata.getColumnCount(); index++) row.add(resultSet.getObject(index));
            rows.add(row);
        }
        return new WorkspaceExecutionResponse(columns, rows, 0, statementType, elapsedMilliseconds(startedAt), null);
    }

    private String connectionUrl(DatabaseWorkspace workspace) {
        if (!INTERNAL_ID.matcher(workspace.internalWorkspaceId()).matches()) {
            throw new WorkspaceException("Invalid internal workspace identifier.");
        }
        Path databasePath = storageDirectory.resolve(workspace.internalWorkspaceId()).normalize();
        if (!databasePath.startsWith(storageDirectory)) throw new WorkspaceException("Invalid workspace storage path.");
        return "jdbc:h2:file:" + databasePath + ";MODE=PostgreSQL;DATABASE_TO_UPPER=false";
    }

    private WorkspaceException sqlException(SQLException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) message = "The SQL operation could not be completed.";
        int details = message.indexOf("; SQL statement:");
        if (details > 0) message = message.substring(0, details);
        return new WorkspaceException(message, exception);
    }

    private long elapsedMilliseconds(long startedAt) {
        return Math.max(1, (System.nanoTime() - startedAt) / 1_000_000);
    }
}

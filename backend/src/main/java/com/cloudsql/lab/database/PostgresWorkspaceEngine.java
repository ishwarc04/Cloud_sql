package com.cloudsql.lab.database;

import com.cloudsql.lab.database.dto.WorkspaceExecutionResponse;
import com.cloudsql.lab.database.model.DatabaseWorkspace;
import com.cloudsql.lab.database.model.WorkspaceColumn;
import com.cloudsql.lab.database.model.WorkspaceTable;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class PostgresWorkspaceEngine implements WorkspaceEngine {
    private static final Pattern INTERNAL_ID = Pattern.compile("[0-9a-f]{32}");
    private final DataSource dataSource;
    private final WorkspaceProperties properties;

    public PostgresWorkspaceEngine(DataSource dataSource, WorkspaceProperties properties) {
        this.dataSource = new org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy(dataSource);
        this.properties = properties;
    }

    @Override
    public long create(DatabaseWorkspace workspace) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schemaName(workspace));
            return storageUsedBytes(connection, workspace);
        } catch (SQLException exception) {
            throw sqlException(exception);
        }
    }

    @Override
    public WorkspaceExecutionResponse execute(DatabaseWorkspace workspace, String sql, String statementType) {
        if (!java.util.Set.of("SELECT", "DELETE", "DROP").contains(statementType) && storageUsedBytes(workspace) >= workspace.storageLimitBytes()) {
            throw new WorkspaceQuotaException("This database has reached its configured storage limit.");
        }

        long startedAt = System.nanoTime();
        try (Connection connection = scopedConnection(workspace); Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(properties.queryTimeoutSeconds());
            statement.setMaxRows(properties.maxReturnedRows());
            boolean hasRows = statement.execute(sql);
            WorkspaceExecutionResponse response;
            if (hasRows) {
                try (ResultSet resultSet = statement.getResultSet()) {
                    response = readRows(resultSet, statementType, startedAt);
                }
            } else {
                response = new WorkspaceExecutionResponse(List.of(), List.of(), Math.max(0, statement.getUpdateCount()),
                        statementType, elapsedMilliseconds(startedAt), null);
            }
            if (!java.util.Set.of("SELECT", "DELETE", "DROP").contains(statementType) && storageUsedBytes(connection, workspace) > workspace.storageLimitBytes()) {
                connection.rollback();
                throw new WorkspaceQuotaException("Write rejected: it would exceed this workspace's storage quota.");
            }
            if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) connection.commit();
            return response;
        } catch (SQLException exception) {
            throw sqlException(exception);
        }
    }

    @Override
    public List<WorkspaceTable> inspectSchema(DatabaseWorkspace workspace) {
        String schema = schemaName(workspace);
        List<WorkspaceTable> tables = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet tableRows = metadata.getTables(null, schema, "%", new String[] { "TABLE" })) {
                while (tableRows.next()) {
                    String tableName = tableRows.getString("TABLE_NAME");
                    List<WorkspaceColumn> columns = new ArrayList<>();
                    try (ResultSet columnRows = metadata.getColumns(null, schema, tableName, "%")) {
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
        try (Connection connection = dataSource.getConnection()) { return storageUsedBytes(connection, workspace); }
        catch (SQLException exception) { throw sqlException(exception); }
    }
    private long storageUsedBytes(Connection connection, DatabaseWorkspace workspace) throws SQLException {
        String sql = """
                SELECT COALESCE(SUM(pg_total_relation_size(format('%I.%I', schemaname, tablename)::regclass)), 0)
                FROM pg_tables WHERE schemaname = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(properties.queryTimeoutSeconds());
            statement.setString(1, schemaName(workspace));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0;
            }
        }
    }

    @Override
    public void delete(DatabaseWorkspace workspace) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schemaName(workspace) + " CASCADE");
        } catch (SQLException exception) {
            throw sqlException(exception);
        }
    }

    private Connection scopedConnection(DatabaseWorkspace workspace) throws SQLException {
        return scopedConnection(workspace, false);
    }
    private Connection scopedConnection(DatabaseWorkspace workspace, boolean snapshot) throws SQLException {
        Connection connection = dataSource.getConnection();
        try {
            if (snapshot && !org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET LOCAL search_path TO " + schemaName(workspace) + ", pg_temp");
                statement.execute("SET LOCAL statement_timeout = '" + properties.queryTimeoutSeconds() + "s'");
            }
            return connection;
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
    }
    @Override public WorkspaceSnapshot backup(DatabaseWorkspace workspace) {
        try (Connection connection = scopedConnection(workspace, true)) {
            return WorkspaceSnapshots.export(connection, schemaName(workspace), workspace.name(), properties.queryTimeoutSeconds());
        } catch (SQLException exception) { throw sqlException(exception); }
    }
    @Override public void restore(DatabaseWorkspace workspace, WorkspaceSnapshot snapshot) {
        try (Connection connection = scopedConnection(workspace)) {
            WorkspaceSnapshots.restore(connection, snapshot, properties.queryTimeoutSeconds());
            if (storageUsedBytes(connection, workspace) > workspace.storageLimitBytes()) { connection.rollback(); throw new WorkspaceQuotaException("Restore would exceed workspace storage quota."); }
            if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) connection.commit();
        } catch (SQLException exception) { throw sqlException(exception); }
    }

    private String schemaName(DatabaseWorkspace workspace) {
        if (!INTERNAL_ID.matcher(workspace.internalWorkspaceId()).matches()) {
            throw new WorkspaceException("Invalid internal workspace identifier.");
        }
        return "workspace_" + workspace.internalWorkspaceId();
    }

    private WorkspaceExecutionResponse readRows(ResultSet resultSet, String statementType, long startedAt) throws SQLException {
        ResultSetMetaData metadata = resultSet.getMetaData();
        List<String> columns = new ArrayList<>();
        for (int index = 1; index <= metadata.getColumnCount(); index++) columns.add(metadata.getColumnLabel(index));
        List<List<Object>> rows = new ArrayList<>();
        while (resultSet.next()) {
            List<Object> row = new ArrayList<>();
            for (int index = 1; index <= metadata.getColumnCount(); index++) row.add(com.cloudsql.lab.common.SqlCellValues.jsonValue(resultSet.getObject(index)));
            rows.add(row);
        }
        return new WorkspaceExecutionResponse(columns, rows, 0, statementType, elapsedMilliseconds(startedAt), null);
    }

    private WorkspaceException sqlException(SQLException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) message = "The SQL operation could not be completed.";
        int detail = message.indexOf("\n  Position:");
        if (detail > 0) message = message.substring(0, detail);
        return new WorkspaceException(message, exception);
    }

    private long elapsedMilliseconds(long startedAt) {
        return Math.max(1, (System.nanoTime() - startedAt) / 1_000_000);
    }
}

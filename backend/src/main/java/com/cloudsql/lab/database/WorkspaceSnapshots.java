package com.cloudsql.lab.database;

import java.sql.*;
import java.time.*;
import java.util.*;

public final class WorkspaceSnapshots {
    private WorkspaceSnapshots() { }
    public static final int MAX_ROWS = 5000, MAX_BYTES = 2_000_000;
    public static WorkspaceSnapshot export(Connection connection, String schema, String name, int timeout) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData(); List<WorkspaceSnapshot.Table> tables = new ArrayList<>();
        int totalRows = 0, bytes = 0;
        try (ResultSet tableRows = metadata.getTables(null, schema, "%", new String[]{"TABLE"})) {
            while (tableRows.next()) {
                if (tables.size() >= 20) fail("Backups support at most 20 tables.");
                String table = tableRows.getString("TABLE_NAME"); quote(table);
                List<WorkspaceSnapshot.Column> columns = new ArrayList<>();
                try (ResultSet cols = metadata.getColumns(null, schema, table, "%")) {
                    while (cols.next()) {
                        if (columns.size() >= 30) fail("Backups support at most 30 columns per table.");
                        String column = cols.getString("COLUMN_NAME"); quote(column);
                        if ("YES".equals(cols.getString("IS_AUTOINCREMENT")) || "YES".equals(cols.getString("IS_GENERATEDCOLUMN")) || cols.getString("COLUMN_DEF") != null)
                            fail("Data snapshots currently require plain columns without generated values or defaults.");
                        columns.add(new WorkspaceSnapshot.Column(column, columnType(cols), cols.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls));
                    }
                }
                try (ResultSet foreignKeys = metadata.getImportedKeys(null, schema, table)) { if (foreignKeys.next()) fail("Data snapshots currently require tables without foreign keys."); }
                SortedMap<Integer, String> keys = new TreeMap<>();
                try (ResultSet primary = metadata.getPrimaryKeys(null, schema, table)) { while (primary.next()) keys.put(primary.getInt("KEY_SEQ"), primary.getString("COLUMN_NAME")); }
                List<List<String>> rows = new ArrayList<>();
                String sql = "SELECT " + String.join(",", columns.stream().map(c -> quote(c.name())).toList()) + " FROM " + quote(schema) + "." + quote(table);
                try (Statement statement = connection.createStatement()) {
                    statement.setQueryTimeout(timeout); statement.setMaxRows(MAX_ROWS + 1);
                    try (ResultSet result = statement.executeQuery(sql)) {
                        while (result.next()) {
                            if (++totalRows > MAX_ROWS) fail("Backups support at most 5,000 rows in total.");
                            List<String> row = new ArrayList<>();
                            for (int i = 0; i < columns.size(); i++) {
                                String value = columns.get(i).type().equals("TIMESTAMP WITH TIME ZONE") && result.getObject(i + 1) != null ? result.getObject(i + 1, OffsetDateTime.class).toString() : result.getString(i + 1);
                                if (value != null) { bytes += value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length; if (bytes > MAX_BYTES) fail("Backup data exceeds the 2 MB limit."); }
                                row.add(value);
                            }
                            rows.add(row);
                        }
                    }
                }
                tables.add(new WorkspaceSnapshot.Table(table, columns, new ArrayList<>(keys.values()), rows));
            }
        }
        return new WorkspaceSnapshot(1, name, Instant.now().toString(), tables);
    }
    public static void restore(Connection connection, WorkspaceSnapshot snapshot, int timeout) throws SQLException {
        if (snapshot.version() != 1) fail("Unsupported snapshot version.");
        for (WorkspaceSnapshot.Table table : snapshot.tables()) {
            List<String> definitions = new ArrayList<>();
            for (WorkspaceSnapshot.Column column : table.columns()) {
                if (!column.type().matches("INTEGER|BIGINT|SMALLINT|BOOLEAN|TEXT|REAL|DOUBLE PRECISION|DATE|TIMESTAMP|TIMESTAMP WITH TIME ZONE|VARCHAR\\([0-9]{1,5}\\)|CHAR\\([0-9]{1,5}\\)|DECIMAL\\([0-9]{1,2},[0-9]{1,2}\\)")) fail("Unsupported snapshot column type.");
                definitions.add(quote(column.name()) + " " + column.type() + (column.nullable() ? "" : " NOT NULL"));
            }
            if (!table.primaryKey().isEmpty()) definitions.add("PRIMARY KEY (" + String.join(",", table.primaryKey().stream().map(WorkspaceSnapshots::quote).toList()) + ")");
            try (Statement statement = connection.createStatement()) { statement.setQueryTimeout(timeout); statement.execute("CREATE TABLE " + quote(table.name()) + " (" + String.join(",", definitions) + ")"); }
            String sql = "INSERT INTO " + quote(table.name()) + " VALUES (" + String.join(",", Collections.nCopies(table.columns().size(), "?")) + ")";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(timeout);
                for (List<String> row : table.rows()) {
                    for (int i = 0; i < row.size(); i++) {
                        String value = row.get(i), type = table.columns().get(i).type();
                        if (value == null) statement.setObject(i + 1, null);
                        else if (type.equals("INTEGER") || type.equals("BIGINT") || type.equals("SMALLINT")) statement.setLong(i + 1, Long.parseLong(value));
                        else if (type.startsWith("DECIMAL")) statement.setBigDecimal(i + 1, new java.math.BigDecimal(value));
                        else if (type.equals("REAL") || type.equals("DOUBLE PRECISION")) statement.setDouble(i + 1, Double.parseDouble(value));
                        else if (type.equals("BOOLEAN")) statement.setBoolean(i + 1, value.equalsIgnoreCase("TRUE") || value.equalsIgnoreCase("t"));
                        else if (type.equals("DATE")) statement.setDate(i + 1, java.sql.Date.valueOf(value));
                        else if (type.equals("TIMESTAMP")) statement.setTimestamp(i + 1, Timestamp.valueOf(value));
                        else if (type.equals("TIMESTAMP WITH TIME ZONE")) statement.setObject(i + 1, OffsetDateTime.parse(value));
                        else statement.setString(i + 1, value);
                    }
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }
    }
    private static String columnType(ResultSet column) throws SQLException {
        if ("timestamptz".equalsIgnoreCase(column.getString("TYPE_NAME"))) return "TIMESTAMP WITH TIME ZONE";
        return switch (column.getInt("DATA_TYPE")) {
            case Types.INTEGER -> "INTEGER"; case Types.BIGINT -> "BIGINT"; case Types.SMALLINT -> "SMALLINT";
            case Types.BOOLEAN, Types.BIT -> "BOOLEAN"; case Types.REAL -> "REAL"; case Types.FLOAT, Types.DOUBLE -> "DOUBLE PRECISION";
            case Types.DATE -> "DATE"; case Types.TIMESTAMP -> "TIMESTAMP"; case Types.TIMESTAMP_WITH_TIMEZONE -> "TIMESTAMP WITH TIME ZONE";
            case Types.VARCHAR, Types.LONGVARCHAR -> column.getInt("COLUMN_SIZE") > 10000 ? "TEXT" : "VARCHAR(" + column.getInt("COLUMN_SIZE") + ")";
            case Types.CHAR -> "CHAR(" + Math.min(10000, column.getInt("COLUMN_SIZE")) + ")";
            case Types.NUMERIC, Types.DECIMAL -> {
                int precision = column.getInt("COLUMN_SIZE"), scale = column.getInt("DECIMAL_DIGITS");
                if (precision < 1 || precision > 38 || scale < 0 || scale > precision) fail("Snapshot decimals require precision 1–38 and a non-negative scale.");
                yield "DECIMAL(" + precision + "," + scale + ")";
            }
            default -> throw new WorkspaceException("This table has a column type that data snapshots do not support.");
        };
    }
    public static String quote(String name) {
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]{0,62}")) fail("Snapshots require simple SQL identifiers.");
        return "\"" + name + "\"";
    }
    private static void fail(String message) { throw new WorkspaceException(message); }
}

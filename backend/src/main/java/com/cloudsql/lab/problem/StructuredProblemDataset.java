package com.cloudsql.lab.problem;

import com.cloudsql.lab.problem.model.*;
import java.sql.*;
import java.util.*;

/** Dataset DDL is generated from validated identifiers/types; values use prepared statements. */
public final class StructuredProblemDataset {
    private StructuredProblemDataset() { }
    public static void validate(List<TablePreview> tables) {
        if (tables == null || tables.isEmpty() || tables.size() > 6) fail("Provide 1–6 sample tables.");
        Set<String> names = new HashSet<>();
        int cells = 0;
        for (TablePreview table : tables) {
            if (table == null || !identifier(table.name()) || !names.add(table.name())) fail("Table names must be unique lowercase SQL identifiers.");
            if (table.columns() == null || table.columns().isEmpty() || table.columns().size() > 12) fail("Provide 1–12 columns per table.");
            Set<String> columns = new HashSet<>();
            for (ColumnPreview column : table.columns()) {
                if (column == null || !identifier(column.name()) || !columns.add(column.name())) fail("Column names must be unique lowercase SQL identifiers.");
                if (column.type() == null || !Set.of("INTEGER", "DECIMAL", "VARCHAR", "DATE", "BOOLEAN").contains(column.type())) fail("Unsupported column type.");
            }
            if (table.sampleRows() == null || table.sampleRows().isEmpty() || table.sampleRows().size() > 100) fail("Provide 1–100 rows per table.");
            for (List<Object> row : table.sampleRows()) {
                if (row == null || row.size() != table.columns().size()) fail("Every row must match its table's columns.");
                cells += row.size();
                for (int i = 0; i < row.size(); i++) {
                    Object value = row.get(i);
                    if (value == null) continue;
                    if (value.toString().length() > 500) fail("Sample values must be at most 500 characters.");
                    try {
                        switch (table.columns().get(i).type()) {
                            case "INTEGER" -> new java.math.BigDecimal(value.toString()).intValueExact();
                            case "DECIMAL" -> { var number = new java.math.BigDecimal(value.toString()); if (number.scale() > 4 || number.precision() - number.scale() > 14) fail("Decimals support 14 whole digits and 4 decimal places."); }
                            case "DATE" -> java.time.LocalDate.parse(value.toString());
                            case "BOOLEAN" -> { if (!(value instanceof Boolean)) fail("Boolean values must be true or false."); }
                            case "VARCHAR" -> { if (!(value instanceof String)) fail("VARCHAR values must be strings."); }
                        }
                    } catch (IllegalArgumentException | ArithmeticException | java.time.DateTimeException exception) { fail("Sample value does not match column type " + table.columns().get(i).type() + "."); }
                }
            }
        }
        if (cells > 3000) fail("Sample dataset supports at most 3,000 cells.");
    }
    private static boolean identifier(String name) {
        return name != null && name.matches("[a-z][a-z0-9_]{0,39}") && !name.startsWith("pg_") && !name.startsWith("practice_") && !name.startsWith("workspace_") && !Set.of("platform", "information_schema", "sys", "system_lobs").contains(name);
    }
    private static void fail(String message) { throw new QueryValidationException(message); }
    public static void populate(Connection connection, List<TablePreview> tables) throws SQLException {
        validate(tables);
        for (TablePreview table : tables) {
            String ddl = "CREATE TABLE " + table.name() + " (" + String.join(", ", table.columns().stream().map(column -> column.name() + " " + switch (column.type()) { case "VARCHAR" -> "VARCHAR(500)"; case "DECIMAL" -> "DECIMAL(18,4)"; default -> column.type(); }).toList()) + ")";
            try (Statement statement = connection.createStatement()) { statement.execute(ddl); }
            String insert = "INSERT INTO " + table.name() + " VALUES (" + String.join(",", Collections.nCopies(table.columns().size(), "?")) + ")";
            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                for (List<Object> row : table.sampleRows()) {
                    for (int i = 0; i < row.size(); i++) {
                        Object value = row.get(i);
                        String type = table.columns().get(i).type();
                        int sqlType = switch (type) { case "INTEGER" -> Types.INTEGER; case "DECIMAL" -> Types.DECIMAL; case "DATE" -> Types.DATE; case "BOOLEAN" -> Types.BOOLEAN; default -> Types.VARCHAR; };
                        if (value == null) statement.setNull(i + 1, sqlType);
                        else switch (type) {
                            case "INTEGER" -> statement.setInt(i + 1, new java.math.BigDecimal(value.toString()).intValueExact());
                            case "DECIMAL" -> statement.setBigDecimal(i + 1, new java.math.BigDecimal(value.toString()));
                            case "DATE" -> statement.setDate(i + 1, java.sql.Date.valueOf(value.toString()));
                            case "BOOLEAN" -> statement.setBoolean(i + 1, (Boolean) value);
                            default -> statement.setString(i + 1, (String) value);
                        }
                    }
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }
    }
}

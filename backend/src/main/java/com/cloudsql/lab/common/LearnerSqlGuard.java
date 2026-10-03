package com.cloudsql.lab.common;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Conservative shared guard: learner SQL cannot call arbitrary database functions or change session settings. */
public final class LearnerSqlGuard {
    private LearnerSqlGuard() { }
    private static final Set<String> SAFE_CALLS = Set.of(
        "COUNT", "SUM", "AVG", "MIN", "MAX", "ROUND", "ABS", "CEIL", "CEILING", "FLOOR", "MOD", "POWER", "SQRT",
        "COALESCE", "NULLIF", "GREATEST", "LEAST", "LOWER", "UPPER", "LENGTH", "CHAR_LENGTH", "CONCAT", "CONCAT_WS",
        "TRIM", "LTRIM", "RTRIM", "SUBSTRING", "SUBSTR", "REPLACE", "LEFT", "RIGHT", "POSITION", "EXTRACT", "DATE_PART", "DATE_TRUNC",
        "CAST", "ROW_NUMBER", "RANK", "DENSE_RANK", "NTILE", "LAG", "LEAD", "FIRST_VALUE", "LAST_VALUE", "STRING_AGG",
        "IN", "EXISTS", "OVER", "FROM", "JOIN", "AS", "ON", "WHERE", "AND", "OR", "NOT", "HAVING", "FILTER", "VALUES",
        "VARCHAR", "CHAR", "DECIMAL", "NUMERIC", "CHECK", "KEY", "UNIQUE", "REFERENCES", "TABLE"
    );
    private static final Pattern CALL = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
    public static String inspect(String sql) {
        if (sql == null || sql.length() > 20000) throw new IllegalArgumentException("SQL must be at most 20,000 characters.");
        // Reject escape/dollar syntax before removing literals, so alternate quoting cannot bypass inspection.
        if (sql.indexOf('\\') >= 0 || sql.indexOf('$') >= 0 || sql.matches("(?s).*\\b[Uu]&.*"))
            throw new IllegalArgumentException("Escape and dollar quoting are not supported.");
        String normalized = sql.replaceAll("'(?:[^']|'')*'", "''");
        var quoted = Pattern.compile("\"([^\"]*)\"").matcher(normalized);
        while (quoted.find()) if (!quoted.group(1).matches("[A-Za-z_][A-Za-z0-9_]*"))
            throw new IllegalArgumentException("Use simple SQL identifiers.");
        normalized = normalized.replace("\"", "");
        if (Pattern.compile("(?i)\\b(PLATFORM|INFORMATION_SCHEMA|PG_[A-Z0-9_]*|WORKSPACE_[A-Z0-9_]*|PRACTICE_[A-Z0-9_]*|SYS|SYSTEM_LOBS)\\b").matcher(normalized).find())
            throw new IllegalArgumentException("Access to platform, practice, internal, or administrative resources is not allowed.");
        if (Pattern.compile("(?i)\\b(FROM|JOIN|INTO|UPDATE|TABLE|REFERENCES)\\s+(?:ONLY\\s+)?[A-Z_][A-Z0-9_]*\\s*\\.").matcher(normalized).find())
            throw new IllegalArgumentException("Schema-qualified tables are not allowed.");
        var calls = CALL.matcher(normalized);
        while (calls.find()) {
            if (!SAFE_CALLS.contains(calls.group(1).toUpperCase(Locale.ROOT))) {
                // CREATE/INSERT table names followed by column lists are identifiers, not function calls.
                String prefix = normalized.substring(0, calls.start()).trim().toUpperCase(Locale.ROOT);
                if (!prefix.matches("(?s).*(?:CREATE\\s+TABLE(?:\\s+IF\\s+NOT\\s+EXISTS)?|INSERT\\s+INTO|REFERENCES)"))
                    throw new IllegalArgumentException("This SQL function is not supported in learner workspaces.");
            }
        }
        return normalized;
    }
}

package com.cloudsql.lab.database;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceSqlValidator {
    private static final Pattern FIRST_WORD = Pattern.compile("^([A-Za-z]+)\\b");
    private static final Set<String> ALLOWED = Set.of("SELECT", "CREATE", "INSERT", "UPDATE", "DELETE", "ALTER", "DROP");
    private static final Pattern FORBIDDEN_TARGET = Pattern.compile(
            "(?i)\\b(INFORMATION_SCHEMA|PG_[A-Z0-9_]*|SYS|SYSTEM_LOBS|PLATFORM|PRACTICE_[A-Z0-9_]*|WORKSPACE_[A-Z0-9_]*|DATABASE_WORKSPACES|USER_PROBLEM_PROGRESS)\\b");
    private static final Pattern QUALIFIED_TARGET = Pattern.compile(
            "(?i)\\b(FROM|JOIN|INTO|UPDATE|TABLE)\\s+[A-Z0-9_\"]+\\.");
    private static final Pattern FORBIDDEN_FUNCTION = Pattern.compile(
            "(?i)\\b(FILE_READ|FILE_WRITE|CSVREAD|CSVWRITE|LINK_SCHEMA|RUNSCRIPT|BACKUP|SHUTDOWN|SCRIPT|GRANT|REVOKE|CREATE USER|ALTER USER|DROP USER)\\b");

    public ValidatedSql validate(String rawSql) {
        if (rawSql == null || rawSql.isBlank()) throw new WorkspaceException("SQL is required.");
        String sql = rawSql.trim();
        if (sql.endsWith(";")) sql = sql.substring(0, sql.length() - 1).trim();
        if (sql.contains(";")) throw new WorkspaceException("Only one SQL statement is allowed.");
        if (sql.contains("--") || sql.contains("/*") || sql.contains("*/")) {
            throw new WorkspaceException("SQL comments are not supported in this phase.");
        }

        Matcher matcher = FIRST_WORD.matcher(sql);
        if (!matcher.find()) throw new WorkspaceException("A supported SQL statement is required.");
        String operation = matcher.group(1).toUpperCase(Locale.ROOT);
        if (!ALLOWED.contains(operation)) throw new WorkspaceException("This SQL operation is not allowed in personal workspaces.");

        String upper = sql.toUpperCase(Locale.ROOT);
        if (operation.equals("CREATE") && !upper.matches("CREATE\\s+TABLE\\b[\\s\\S]*")) rejectOperation();
        if (operation.equals("ALTER") && !upper.matches("ALTER\\s+TABLE\\b[\\s\\S]*")) rejectOperation();
        if (operation.equals("DROP") && !upper.matches("DROP\\s+TABLE\\b[\\s\\S]*")) rejectOperation();
        if (operation.equals("INSERT") && !upper.matches("INSERT\\s+INTO\\b[\\s\\S]*")) rejectOperation();
        if (operation.equals("DELETE") && !upper.matches("DELETE\\s+FROM\\b[\\s\\S]*")) rejectOperation();
        if (FORBIDDEN_TARGET.matcher(sql).find() || FORBIDDEN_FUNCTION.matcher(sql).find() || QUALIFIED_TARGET.matcher(sql).find()) {
            throw new WorkspaceException("Access to platform, practice, internal, or administrative resources is not allowed.");
        }
        return new ValidatedSql(sql, operation);
    }

    private void rejectOperation() {
        throw new WorkspaceException("Only table-level SQL operations are allowed.");
    }

    public record ValidatedSql(String sql, String operation) {
    }
}

package com.cloudsql.lab.problem;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class QuerySafetyValidator {
    private static final Pattern SELECT_START = Pattern.compile("^SELECT\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORBIDDEN_KEYWORD = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|MERGE|REPLACE|GRANT|REVOKE|CALL|EXECUTE|SET)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FORBIDDEN_NAMESPACE = Pattern.compile(
            "\\b(PLATFORM|PRACTICE_PROBLEM_[A-Z0-9_]*|WORKSPACE_[A-Z0-9_]*|INFORMATION_SCHEMA|PG_[A-Z0-9_]*)\\b|\\b(FROM|JOIN)\\s+[A-Z0-9_\"]+\\.",
            Pattern.CASE_INSENSITIVE);

    public String validate(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isEmpty()) {
            throw new QueryValidationException("Query is required.");
        }
        if (query.contains("--") || query.contains("/*") || query.contains("*/")) {
            throw new QueryValidationException("SQL comments are not allowed in this execution environment.");
        }

        String statement = stripSingleTrailingSemicolon(query);
        if (statement.indexOf(';') >= 0) {
            throw new QueryValidationException("Only one SQL statement may be executed at a time.");
        }
        if (!SELECT_START.matcher(statement).find()) {
            throw new QueryValidationException("Only SELECT statements are allowed.");
        }
        if (FORBIDDEN_KEYWORD.matcher(statement.toUpperCase(Locale.ROOT)).find()) {
            throw new QueryValidationException("The query contains a statement or keyword that is not allowed.");
        }
        if (FORBIDDEN_NAMESPACE.matcher(statement).find()) {
            throw new QueryValidationException("Queries may only access tables in this problem's isolated dataset.");
        }
        return statement;
    }

    private String stripSingleTrailingSemicolon(String query) {
        return query.endsWith(";") ? query.substring(0, query.length() - 1).trim() : query;
    }
}

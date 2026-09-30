package com.cloudsql.lab.database.dto;

import java.util.List;

public record WorkspaceExecutionResponse(
        List<String> columns,
        List<List<Object>> rows,
        int affectedRows,
        String statementType,
        long executionTimeMs,
        String error) {

    public static WorkspaceExecutionResponse error(String message) {
        return new WorkspaceExecutionResponse(List.of(), List.of(), 0, null, 0, message);
    }
}

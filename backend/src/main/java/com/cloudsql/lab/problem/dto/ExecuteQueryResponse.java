package com.cloudsql.lab.problem.dto;

import java.util.List;

public record ExecuteQueryResponse(
        List<String> columns,
        List<List<Object>> rows,
        long executionTimeMs,
        String error) {

    public static ExecuteQueryResponse error(String message) {
        return new ExecuteQueryResponse(List.of(), List.of(), 0, message);
    }
}

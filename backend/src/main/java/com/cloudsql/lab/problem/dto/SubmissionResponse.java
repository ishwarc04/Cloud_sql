package com.cloudsql.lab.problem.dto;

import java.util.List;

public record SubmissionResponse(
        List<String> columns,
        List<List<Object>> rows,
        long executionTimeMs,
        String error,
        boolean correct,
        String message,
        String status,
        int attempts) {
}

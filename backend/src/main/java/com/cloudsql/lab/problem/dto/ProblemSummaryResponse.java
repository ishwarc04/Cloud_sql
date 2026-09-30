package com.cloudsql.lab.problem.dto;

public record ProblemSummaryResponse(long id, String slug, String title, String difficulty, String topic, String status, int attempts) {
}

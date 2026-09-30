package com.cloudsql.lab.problem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExecuteQueryRequest(
        @NotBlank(message = "Query is required.")
        @Size(max = 10_000, message = "Query must not exceed 10,000 characters.")
        String query) {
}

package com.cloudsql.lab.database.dto;

import jakarta.validation.constraints.NotBlank;

public record ExecuteWorkspaceRequest(@NotBlank(message = "SQL is required.") String query) {
}

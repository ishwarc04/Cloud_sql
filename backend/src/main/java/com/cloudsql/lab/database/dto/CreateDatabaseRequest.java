package com.cloudsql.lab.database.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDatabaseRequest(
        @NotBlank(message = "Database name is required.")
        @Size(min = 3, max = 60, message = "Database name must be between 3 and 60 characters.")
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9 _-]*", message = "Use letters, numbers, spaces, hyphens, or underscores.")
        String name) {
}

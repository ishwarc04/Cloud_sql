package com.cloudsql.lab.problem.model;

import java.util.List;

public record ProblemDefinition(
        long id,
        String slug,
        String title,
        String difficulty,
        String topic,
        String description,
        String starterQuery,
        String solutionQuery,
        String seedScript,
        List<TablePreview> tables) {
}

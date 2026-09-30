package com.cloudsql.lab.problem.dto;

import com.cloudsql.lab.problem.model.TablePreview;
import java.util.List;

public record ProblemDetailResponse(
        long id,
        String slug,
        String title,
        String difficulty,
        String topic,
        String description,
        String starterQuery,
        List<TablePreview> tables,
        String status,
        int attempts) {
}

package com.cloudsql.lab.problem.model;

import java.util.List;

public record TablePreview(String name, List<ColumnPreview> columns, List<List<Object>> sampleRows) {
}

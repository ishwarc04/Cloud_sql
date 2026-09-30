package com.cloudsql.lab.database.model;

import java.util.List;

public record WorkspaceTable(String name, List<WorkspaceColumn> columns) {
}

package com.cloudsql.lab.database.dto;

import com.cloudsql.lab.database.model.WorkspaceTable;
import java.util.List;

public record WorkspaceSchemaResponse(List<WorkspaceTable> tables) {
}

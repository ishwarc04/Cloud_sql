package com.cloudsql.lab.database;

import com.cloudsql.lab.database.dto.WorkspaceExecutionResponse;
import com.cloudsql.lab.database.model.DatabaseWorkspace;
import com.cloudsql.lab.database.model.WorkspaceTable;
import java.util.List;

public interface WorkspaceEngine {
    long create(DatabaseWorkspace workspace);
    WorkspaceExecutionResponse execute(DatabaseWorkspace workspace, String sql, String statementType);
    List<WorkspaceTable> inspectSchema(DatabaseWorkspace workspace);
    long storageUsedBytes(DatabaseWorkspace workspace);
    void delete(DatabaseWorkspace workspace);
    WorkspaceSnapshot backup(DatabaseWorkspace workspace);
    void restore(DatabaseWorkspace workspace, WorkspaceSnapshot snapshot);
}

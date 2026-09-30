package com.cloudsql.lab.database;

public class WorkspaceNotFoundException extends WorkspaceException {
    public WorkspaceNotFoundException() {
        super("Database workspace was not found or is not accessible.");
    }
}

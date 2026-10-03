package com.cloudsql.lab.database;
import java.time.Instant;
import java.util.List;

/** Portable logical snapshot, not a physical PostgreSQL cluster backup. */
public record WorkspaceSnapshot(int version, String name, String createdAt, List<Table> tables) {
    public record Column(String name, String type, boolean nullable) { }
    public record Table(String name, List<Column> columns, List<String> primaryKey, List<List<String>> rows) { }
}

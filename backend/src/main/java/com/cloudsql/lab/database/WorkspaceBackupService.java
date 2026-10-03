package com.cloudsql.lab.database;

import com.cloudsql.lab.database.model.DatabaseWorkspace;
import com.cloudsql.lab.database.dto.DatabaseResponse;
import com.cloudsql.lab.progress.CurrentUserProvider;
import com.cloudsql.lab.cloud.AuditService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;

@Service
public class WorkspaceBackupService {
    public record Backup(String id, String sourceWorkspaceId, String name, Instant createdAt, long sizeBytes, int tableCount, int rowCount) { }
    private final JdbcTemplate jdbc;
    private final WorkspaceCatalogRepository catalog;
    private final WorkspaceEngine engine;
    private final DatabaseWorkspaceService workspaces;
    private final CurrentUserProvider current;
    private final AuditService audit;
    private final tools.jackson.databind.json.JsonMapper json = tools.jackson.databind.json.JsonMapper.builder().build();
    public WorkspaceBackupService(JdbcTemplate jdbc, WorkspaceCatalogRepository catalog, WorkspaceEngine engine, DatabaseWorkspaceService workspaces, CurrentUserProvider current, AuditService audit) {
        this.jdbc = jdbc; this.catalog = catalog; this.engine = engine; this.workspaces = workspaces; this.current = current; this.audit = audit;
    }
    public List<Backup> list() {
        return jdbc.query("SELECT * FROM platform.workspace_backups WHERE owner_user_id = ? ORDER BY created_at DESC", (rs, row) -> new Backup(rs.getString("id"), rs.getString("source_workspace_id"), rs.getString("name"), rs.getTimestamp("created_at").toInstant(), rs.getLong("size_bytes"), rs.getInt("table_count"), rs.getInt("row_count")), current.currentUserId());
    }
    @Transactional public Backup create(String id) {
        String owner = current.currentUserId(); workspaces.lockOwner(owner);
        DatabaseWorkspace workspace = catalog.findByIdAndOwner(id, owner).orElseThrow(WorkspaceNotFoundException::new);
        if (list().size() >= 3) throw new WorkspaceQuotaException("Backup quota reached. Keep at most 3 saved snapshots per account.");
        WorkspaceSnapshot snapshot = engine.backup(workspace);
        String payload = json.writeValueAsString(snapshot); long size = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (size > WorkspaceSnapshots.MAX_BYTES) throw new WorkspaceQuotaException("Snapshot exceeds the 2 MB backup limit.");
        var backup = new Backup(UUID.randomUUID().toString(), id, workspace.name(), Instant.now(), size, snapshot.tables().size(), snapshot.tables().stream().mapToInt(t -> t.rows().size()).sum());
        jdbc.update("INSERT INTO platform.workspace_backups VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", backup.id(), owner, id, backup.name(), Timestamp.from(backup.createdAt()), size, backup.tableCount(), backup.rowCount(), payload);
        afterCommit(owner, "BACKUP_CREATED", backup.id()); return backup;
    }
    public WorkspaceSnapshot download(String id) {
        WorkspaceSnapshot snapshot = read(id); audit.record(current.currentUserId(), "BACKUP_DOWNLOADED", id, "SUCCESS"); return snapshot;
    }
    @Transactional public DatabaseResponse restore(String id, String name) {
        String owner = current.currentUserId(); workspaces.lockOwner(owner);
        WorkspaceSnapshot snapshot = read(id);
        DatabaseResponse created = workspaces.create(name);
        DatabaseWorkspace workspace = catalog.findByIdAndOwner(created.id(), owner).orElseThrow(WorkspaceNotFoundException::new);
        try {
            engine.restore(workspace, snapshot); long size = engine.storageUsedBytes(workspace); catalog.updateStorageUsed(workspace.id(), size);
            afterCommit(owner, "BACKUP_RESTORED", id); return DatabaseResponse.from(workspace.withStorageUsedBytes(size));
        } catch (RuntimeException exception) {
            engine.delete(workspace); throw exception;
        }
    }
    @Transactional public void delete(String id) {
        String owner = current.currentUserId(); workspaces.lockOwner(owner); read(id);
        jdbc.update("DELETE FROM platform.workspace_backups WHERE id = ? AND owner_user_id = ?", id, owner);
        afterCommit(owner, "BACKUP_DELETED", id);
    }
    private WorkspaceSnapshot read(String id) {
        String payload = jdbc.query("SELECT payload FROM platform.workspace_backups WHERE id = ? AND owner_user_id = ?", (rs, row) -> rs.getString(1), id, current.currentUserId()).stream().findFirst().orElseThrow(WorkspaceNotFoundException::new);
        return json.readValue(payload, WorkspaceSnapshot.class);
    }
    private void afterCommit(String actor, String action, String target) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void beforeCommit(boolean readOnly) { audit.recordInTransaction(actor, action, target, "SUCCESS"); } });
    }
}

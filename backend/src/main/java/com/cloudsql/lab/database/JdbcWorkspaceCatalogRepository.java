package com.cloudsql.lab.database;

import com.cloudsql.lab.database.model.DatabaseWorkspace;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkspaceCatalogRepository implements WorkspaceCatalogRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkspaceCatalogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<DatabaseWorkspace> findAll() {
        return jdbcTemplate.query("SELECT * FROM platform.database_workspaces ORDER BY created_at DESC", this::mapWorkspace);
    }

    @Override
    public List<DatabaseWorkspace> findByOwner(String ownerUserId) {
        return jdbcTemplate.query("SELECT * FROM platform.database_workspaces WHERE owner_user_id = ? ORDER BY created_at DESC",
                this::mapWorkspace, ownerUserId);
    }

    @Override
    public Optional<DatabaseWorkspace> findByIdAndOwner(String id, String ownerUserId) {
        return jdbcTemplate.query("SELECT * FROM platform.database_workspaces WHERE id = ? AND owner_user_id = ?",
                this::mapWorkspace, id, ownerUserId).stream().findFirst();
    }

    @Override
    public void save(DatabaseWorkspace workspace) {
        jdbcTemplate.update("""
                INSERT INTO platform.database_workspaces
                  (id, internal_workspace_id, name, owner_user_id, status, created_at, storage_used_bytes, storage_limit_bytes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, workspace.id(), workspace.internalWorkspaceId(), workspace.name(), workspace.ownerUserId(),
                workspace.status(), workspace.createdAt(), workspace.storageUsedBytes(), workspace.storageLimitBytes());
    }

    @Override
    public void updateStorageUsed(String id, long storageUsedBytes) {
        jdbcTemplate.update("UPDATE platform.database_workspaces SET storage_used_bytes = ? WHERE id = ?", storageUsedBytes, id);
    }

    @Override
    public void delete(String id) {
        jdbcTemplate.update("DELETE FROM platform.database_workspaces WHERE id = ?", id);
    }

    private DatabaseWorkspace mapWorkspace(ResultSet resultSet, int rowNumber) throws SQLException {
        return new DatabaseWorkspace(
                resultSet.getString("id"), resultSet.getString("internal_workspace_id"), resultSet.getString("name"),
                resultSet.getString("owner_user_id"), resultSet.getString("status"),
                resultSet.getObject("created_at", java.time.OffsetDateTime.class),
                resultSet.getLong("storage_used_bytes"), resultSet.getLong("storage_limit_bytes"));
    }
}

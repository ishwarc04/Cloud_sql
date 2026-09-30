package com.cloudsql.lab.database;

import com.cloudsql.lab.database.WorkspaceSqlValidator.ValidatedSql;
import com.cloudsql.lab.database.dto.DatabaseListResponse;
import com.cloudsql.lab.database.dto.DatabaseQuotaResponse;
import com.cloudsql.lab.database.dto.DatabaseResponse;
import com.cloudsql.lab.database.dto.WorkspaceExecutionResponse;
import com.cloudsql.lab.database.dto.WorkspaceSchemaResponse;
import com.cloudsql.lab.database.model.DatabaseWorkspace;
import com.cloudsql.lab.progress.CurrentUserProvider;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DatabaseWorkspaceService {
    private final WorkspaceCatalogRepository repository;
    private final WorkspaceEngine engine;
    private final WorkspaceSqlValidator validator;
    private final WorkspaceProperties properties;
    private final CurrentUserProvider currentUserProvider;

    public DatabaseWorkspaceService(WorkspaceCatalogRepository repository, WorkspaceEngine engine,
            WorkspaceSqlValidator validator, WorkspaceProperties properties, CurrentUserProvider currentUserProvider) {
        this.repository = repository;
        this.engine = engine;
        this.validator = validator;
        this.properties = properties;
        this.currentUserProvider = currentUserProvider;
    }

    public DatabaseListResponse findAll() {
        List<DatabaseWorkspace> workspaces = refreshStorage(repository.findByOwner(currentUserProvider.currentUserId()));
        long used = workspaces.stream().mapToLong(DatabaseWorkspace::storageUsedBytes).sum();
        return new DatabaseListResponse(workspaces.stream().map(DatabaseResponse::from).toList(),
                new DatabaseQuotaResponse(workspaces.size(), properties.maxDatabasesPerUser(), used,
                        properties.storageLimitBytes() * properties.maxDatabasesPerUser()));
    }

    public DatabaseResponse findById(String id) {
        DatabaseWorkspace workspace = refreshStorage(getOwned(id));
        return DatabaseResponse.from(workspace);
    }

    public DatabaseResponse create(String rawName) {
        String owner = currentUserProvider.currentUserId();
        if (repository.findByOwner(owner).size() >= properties.maxDatabasesPerUser()) {
            throw new WorkspaceQuotaException("Database quota reached. Delete a database before creating another.");
        }
        String publicId = UUID.randomUUID().toString();
        String internalId = UUID.randomUUID().toString().replace("-", "");
        DatabaseWorkspace workspace = new DatabaseWorkspace(publicId, internalId, rawName.trim(), owner, "ACTIVE",
                OffsetDateTime.now(ZoneOffset.UTC), 0, properties.storageLimitBytes());
        long initialBytes = engine.create(workspace);
        workspace = workspace.withStorageUsedBytes(initialBytes);
        try {
            repository.save(workspace);
        } catch (RuntimeException exception) {
            engine.delete(workspace);
            throw exception;
        }
        return DatabaseResponse.from(workspace);
    }

    public WorkspaceExecutionResponse execute(String id, String rawSql) {
        DatabaseWorkspace workspace = getOwned(id);
        ValidatedSql sql = validator.validate(rawSql);
        WorkspaceExecutionResponse response = engine.execute(workspace, sql.sql(), sql.operation());
        repository.updateStorageUsed(workspace.id(), engine.storageUsedBytes(workspace));
        return response;
    }

    public WorkspaceSchemaResponse inspectSchema(String id) {
        return new WorkspaceSchemaResponse(engine.inspectSchema(getOwned(id)));
    }

    public void delete(String id) {
        DatabaseWorkspace workspace = getOwned(id);
        engine.delete(workspace);
        repository.delete(workspace.id());
    }

    private DatabaseWorkspace getOwned(String id) {
        return repository.findByIdAndOwner(id, currentUserProvider.currentUserId()).orElseThrow(WorkspaceNotFoundException::new);
    }

    private List<DatabaseWorkspace> refreshStorage(List<DatabaseWorkspace> workspaces) {
        return workspaces.stream().map(this::refreshStorage).toList();
    }

    private DatabaseWorkspace refreshStorage(DatabaseWorkspace workspace) {
        long bytes = engine.storageUsedBytes(workspace);
        if (bytes != workspace.storageUsedBytes()) repository.updateStorageUsed(workspace.id(), bytes);
        return workspace.withStorageUsedBytes(bytes);
    }
}

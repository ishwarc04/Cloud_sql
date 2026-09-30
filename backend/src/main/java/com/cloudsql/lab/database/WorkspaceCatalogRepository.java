package com.cloudsql.lab.database;

import com.cloudsql.lab.database.model.DatabaseWorkspace;
import java.util.List;
import java.util.Optional;

public interface WorkspaceCatalogRepository {
    List<DatabaseWorkspace> findAll();
    List<DatabaseWorkspace> findByOwner(String ownerUserId);
    Optional<DatabaseWorkspace> findByIdAndOwner(String id, String ownerUserId);
    void save(DatabaseWorkspace workspace);
    void updateStorageUsed(String id, long storageUsedBytes);
    void delete(String id);
}

package com.cloudsql.lab.admin;

import com.cloudsql.lab.admin.dto.AdminDatabaseResponse;
import com.cloudsql.lab.admin.dto.AdminOverviewResponse;
import com.cloudsql.lab.database.WorkspaceCatalogRepository;
import com.cloudsql.lab.database.WorkspaceEngine;
import com.cloudsql.lab.database.model.DatabaseWorkspace;
import com.cloudsql.lab.problem.ProblemRepository;
import com.cloudsql.lab.progress.ProgressRepository;
import com.cloudsql.lab.progress.ProgressStats;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AdminService {
    private final WorkspaceCatalogRepository workspaces;
    private final WorkspaceEngine workspaceEngine;
    private final ProblemRepository problems;
    private final ProgressRepository progress;

    public AdminService(WorkspaceCatalogRepository workspaces, WorkspaceEngine workspaceEngine,
            ProblemRepository problems, ProgressRepository progress) {
        this.workspaces = workspaces;
        this.workspaceEngine = workspaceEngine;
        this.problems = problems;
        this.progress = progress;
    }

    public AdminOverviewResponse overview() {
        List<AdminDatabaseResponse> databases = databases();
        ProgressStats stats = progress.summarize();
        long used = databases.stream().mapToLong(AdminDatabaseResponse::storageUsedBytes).sum();
        long capacity = databases.stream().mapToLong(AdminDatabaseResponse::storageLimitBytes).sum();
        return new AdminOverviewResponse(
                databases.size(), databases.stream().filter(database -> "ACTIVE".equals(database.status())).count(),
                used, capacity, Math.max(0, capacity - used), problems.findAll().size(), stats.solvedProblems(),
                stats.totalAttempts(), "OPERATIONAL", OffsetDateTime.now(ZoneOffset.UTC));
    }

    public List<AdminDatabaseResponse> databases() {
        return workspaces.findAll().stream().map(this::toResponse).toList();
    }

    private AdminDatabaseResponse toResponse(DatabaseWorkspace workspace) {
        long bytes = workspaceEngine.storageUsedBytes(workspace);
        return AdminDatabaseResponse.from(workspace, bytes);
    }
}

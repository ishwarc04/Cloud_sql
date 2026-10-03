package com.cloudsql.lab.database;
import com.cloudsql.lab.database.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class WorkspaceBackupController {
    private final WorkspaceBackupService backups;
    public WorkspaceBackupController(WorkspaceBackupService backups) { this.backups = backups; }
    @GetMapping("/api/backups") public List<WorkspaceBackupService.Backup> list() { return backups.list(); }
    @PostMapping("/api/databases/{id}/backups") @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceBackupService.Backup create(@PathVariable String id) { return backups.create(id); }
    @GetMapping("/api/backups/{id}") public WorkspaceSnapshot download(@PathVariable String id) { return backups.download(id); }
    @PostMapping("/api/backups/{id}/restore") @ResponseStatus(HttpStatus.CREATED)
    public DatabaseResponse restore(@PathVariable String id, @Valid @RequestBody CreateDatabaseRequest input) { return backups.restore(id, input.name()); }
    @DeleteMapping("/api/backups/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable String id) { backups.delete(id); }
}

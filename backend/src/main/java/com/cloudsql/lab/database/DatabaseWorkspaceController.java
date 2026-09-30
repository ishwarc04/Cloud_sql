package com.cloudsql.lab.database;

import com.cloudsql.lab.database.dto.CreateDatabaseRequest;
import com.cloudsql.lab.database.dto.DatabaseListResponse;
import com.cloudsql.lab.database.dto.DatabaseResponse;
import com.cloudsql.lab.database.dto.ExecuteWorkspaceRequest;
import com.cloudsql.lab.database.dto.WorkspaceExecutionResponse;
import com.cloudsql.lab.database.dto.WorkspaceSchemaResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/databases")
public class DatabaseWorkspaceController {
    private final DatabaseWorkspaceService service;

    public DatabaseWorkspaceController(DatabaseWorkspaceService service) {
        this.service = service;
    }

    @GetMapping
    public DatabaseListResponse findAll() {
        return service.findAll();
    }

    @PostMapping
    public ResponseEntity<DatabaseResponse> create(@Valid @RequestBody CreateDatabaseRequest request) {
        DatabaseResponse created = service.create(request.name());
        return ResponseEntity.created(URI.create("/api/databases/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public DatabaseResponse findById(@PathVariable String id) {
        return service.findById(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/execute")
    public WorkspaceExecutionResponse execute(@PathVariable String id, @Valid @RequestBody ExecuteWorkspaceRequest request) {
        return service.execute(id, request.query());
    }

    @GetMapping("/{id}/schema")
    public WorkspaceSchemaResponse inspectSchema(@PathVariable String id) {
        return service.inspectSchema(id);
    }
}

package com.cloudsql.lab.problem;

import com.cloudsql.lab.problem.dto.ExecuteQueryRequest;
import com.cloudsql.lab.problem.dto.ExecuteQueryResponse;
import com.cloudsql.lab.problem.dto.ProblemDetailResponse;
import com.cloudsql.lab.problem.dto.ProblemSummaryResponse;
import com.cloudsql.lab.problem.dto.SubmissionResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/problems")
public class ProblemController {
    private final ProblemService service;

    public ProblemController(ProblemService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProblemSummaryResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ProblemDetailResponse findById(@PathVariable long id) {
        return service.findById(id);
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<ExecuteQueryResponse> execute(@PathVariable long id, @Valid @RequestBody ExecuteQueryRequest request) {
        return ResponseEntity.ok(service.execute(id, request.query()));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<SubmissionResponse> submit(@PathVariable long id, @Valid @RequestBody ExecuteQueryRequest request) {
        return ResponseEntity.ok(service.submit(id, request.query()));
    }
}

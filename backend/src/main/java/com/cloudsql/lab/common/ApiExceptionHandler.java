package com.cloudsql.lab.common;

import com.cloudsql.lab.database.WorkspaceException;
import com.cloudsql.lab.database.WorkspaceNotFoundException;
import com.cloudsql.lab.database.WorkspaceQuotaException;
import com.cloudsql.lab.database.dto.WorkspaceExecutionResponse;
import com.cloudsql.lab.problem.ProblemNotFoundException;
import com.cloudsql.lab.problem.QueryExecutionException;
import com.cloudsql.lab.problem.QueryValidationException;
import com.cloudsql.lab.problem.dto.ExecuteQueryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    ResponseEntity<java.util.Map<String, String>> handleAuth(org.springframework.web.server.ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(java.util.Map.of("error", exception.getReason() == null ? "Request failed." : exception.getReason()));
    }
    @ExceptionHandler(WorkspaceNotFoundException.class)
    ResponseEntity<WorkspaceExecutionResponse> handleWorkspaceNotFound(WorkspaceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(WorkspaceExecutionResponse.error(exception.getMessage()));
    }

    @ExceptionHandler(WorkspaceQuotaException.class)
    ResponseEntity<WorkspaceExecutionResponse> handleWorkspaceQuota(WorkspaceQuotaException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(WorkspaceExecutionResponse.error(exception.getMessage()));
    }

    @ExceptionHandler(WorkspaceException.class)
    ResponseEntity<WorkspaceExecutionResponse> handleWorkspaceError(WorkspaceException exception) {
        return ResponseEntity.badRequest().body(WorkspaceExecutionResponse.error(exception.getMessage()));
    }

    @ExceptionHandler(ProblemNotFoundException.class)
    ResponseEntity<ExecuteQueryResponse> handleNotFound(ProblemNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ExecuteQueryResponse.error(exception.getMessage()));
    }

    @ExceptionHandler({QueryValidationException.class, QueryExecutionException.class})
    ResponseEntity<ExecuteQueryResponse> handleBadQuery(RuntimeException exception) {
        return ResponseEntity.badRequest().body(ExecuteQueryResponse.error(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ExecuteQueryResponse> handleInvalidRequest(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream().findFirst().map(error -> error.getDefaultMessage()).orElse("Invalid request.");
        return ResponseEntity.badRequest().body(ExecuteQueryResponse.error(message));
    }
}

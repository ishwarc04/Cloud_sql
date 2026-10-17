package com.cloudsql.lab.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.cloudsql.lab.cloud.AuditService;

@RestController
@RequestMapping("/api/auth/sessions")
public class SessionManagementController {
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    public SessionManagementController(JdbcTemplate jdbc, AuditService audit) {this.jdbc=jdbc;this.audit=audit;}
    public record Session(String id, Instant createdAt, Instant expiresAt, boolean current) {}
    @GetMapping public List<Session> list(HttpServletRequest request) {
        var account=(Account)request.getAttribute("account");
        // Backfill legacy sessions lazily without exposing authentication hashes as identifiers.
        var legacy=jdbc.queryForList("SELECT token_hash FROM platform.sessions WHERE user_id=? AND session_id IS NULL",String.class,account.id());
        for (String hash:legacy) jdbc.update("UPDATE platform.sessions SET session_id=? WHERE token_hash=? AND session_id IS NULL",UUID.randomUUID().toString(),hash);
        String currentHash=AuthService.digest(SessionFilter.token(request));
        return jdbc.query("SELECT session_id,created_at,expires_at,token_hash FROM platform.sessions WHERE user_id=? AND expires_at>? ORDER BY created_at DESC",
            (r,i)->new Session(r.getString("session_id"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("expires_at").toInstant(),currentHash.equals(r.getString("token_hash"))),account.id(),Timestamp.from(Instant.now()));
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id,HttpServletRequest request) {
        var account=(Account)request.getAttribute("account");
        int removed=jdbc.update("DELETE FROM platform.sessions WHERE session_id=? AND user_id=? AND token_hash<>?",id.toString(),account.id(),AuthService.digest(SessionFilter.token(request)));
        if (removed==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Other active session not found. Use Sign out for this session.");
        audit.record(account.id(),"SESSION_REVOKED","OTHER_SESSION","SUCCESS");
    }
}

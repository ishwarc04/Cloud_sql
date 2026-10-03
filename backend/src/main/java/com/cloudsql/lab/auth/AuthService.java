package com.cloudsql.lab.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final JdbcTemplate jdbc;
    private final PasswordHasher passwords;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;
    private final long sessionHours;
    private final com.cloudsql.lab.cloud.AuditService audit;

    public AuthService(JdbcTemplate jdbc, PasswordHasher passwords,
            @Value("${cloudsql.auth.session-hours:24}") long sessionHours, com.cloudsql.lab.cloud.AuditService audit) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.sessionHours = sessionHours;
        this.audit = audit;
        if (sessionHours < 1 || sessionHours > 720) throw new IllegalArgumentException("Session hours must be 1–720");
        dummyHash = passwords.hash(UUID.randomUUID().toString());
    }

    public Account signup(String name, String email, String password) {
        Account user = new Account(UUID.randomUUID().toString(), name.trim(), normalize(email), "USER");
        try {
            jdbc.update("INSERT INTO platform.users (id, name, email, password_hash, role, created_at) VALUES (?, ?, ?, ?, 'USER', ?)",
                    user.id(), user.name(), user.email(), passwords.hash(password), Timestamp.from(Instant.now()));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unable to create account with this email.");
        }
        var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        Account actor = attributes instanceof org.springframework.web.context.request.ServletRequestAttributes request ? (Account) request.getRequest().getAttribute("account") : null;
        audit.record(actor == null ? user.id() : actor.id(), "ACCOUNT_CREATED", user.id(), "SUCCESS");
        return user;
    }

    public Account login(String email, String password) {
        String normalized = normalize(email);
        // Persisted rolling limit survives process restarts; never log credentials.
        Integer failures = jdbc.queryForObject("SELECT COUNT(*) FROM platform.login_failures WHERE email = ? AND attempted_at > ?",
                Integer.class, normalized, Timestamp.from(Instant.now().minus(Duration.ofMinutes(15))));
        if (failures != null && failures >= 10) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts. Try again in 15 minutes.");
        var users = jdbc.query("SELECT id, name, email, role, password_hash FROM platform.users WHERE email = ?",
                (rs, row) -> new Credentials(new Account(rs.getString("id"), rs.getString("name"), rs.getString("email"), rs.getString("role")), rs.getString("password_hash")), normalized);
        boolean matched = passwords.matches(password, users.isEmpty() ? dummyHash : users.getFirst().hash());
        if (users.isEmpty() || !matched) {
            jdbc.update("INSERT INTO platform.login_failures (id, email, attempted_at) VALUES (?, ?, ?)", UUID.randomUUID().toString(), normalized, Timestamp.from(Instant.now()));
            audit.record(null, "SIGN_IN", "AUTH", "FAILED");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }
        jdbc.update("DELETE FROM platform.login_failures WHERE email = ?", normalized);
        audit.record(users.getFirst().account().id(), "SIGN_IN", "AUTH", "SUCCESS");
        return users.getFirst().account();
    }

    public String createSession(Account user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("DELETE FROM platform.sessions WHERE expires_at <= ?", Timestamp.from(Instant.now()));
        jdbc.update("DELETE FROM platform.login_failures WHERE attempted_at <= ?", Timestamp.from(Instant.now().minus(Duration.ofMinutes(15))));
        jdbc.update("INSERT INTO platform.sessions (token_hash, user_id, created_at, expires_at) VALUES (?, ?, ?, ?)",
                digest(token), user.id(), Timestamp.from(Instant.now()), Timestamp.from(Instant.now().plus(Duration.ofHours(sessionHours))));
        return token;
    }

    public Account resolve(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return null;
        return jdbc.query("SELECT u.id, u.name, u.email, u.role FROM platform.sessions s JOIN platform.users u ON u.id = s.user_id WHERE s.token_hash = ? AND s.expires_at > ?",
                (rs, row) -> new Account(rs.getString("id"), rs.getString("name"), rs.getString("email"), rs.getString("role")), digest(token), Timestamp.from(Instant.now())).stream().findFirst().orElse(null);
    }

    public void revoke(String token) {
        Account account = resolve(token);
        if (token != null) jdbc.update("DELETE FROM platform.sessions WHERE token_hash = ?", digest(token));
        if (account != null) audit.record(account.id(), "SESSION_REVOKED", "AUTH", "SUCCESS");
    }

    public long sessionSeconds() { return sessionHours * 3600; }
    public static String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private record Credentials(Account account, String hash) { }
    private static String digest(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}

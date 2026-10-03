package com.cloudsql.lab.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AdminProvisioner implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordHasher passwords;
    private final String email;
    private final String password;
    public AdminProvisioner(JdbcTemplate jdbc, PasswordHasher passwords, @Value("${cloudsql.auth.admin-email:}") String email,
            @Value("${cloudsql.auth.admin-password:}") String password) {
        this.jdbc = jdbc; this.passwords = passwords; this.email = email; this.password = password;
    }
    @Override public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) return;
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || password.length() < 12 || password.length() > 128)
            throw new IllegalArgumentException("Admin provisioning requires an email and a 12–128 character password");
        String normalized = AuthService.normalize(email);
        // Never promote a publicly registered account or reset an existing admin on restart.
        var roles = jdbc.queryForList("SELECT role FROM platform.users WHERE email = ?", String.class, normalized);
        if (!roles.isEmpty()) {
            if (!roles.getFirst().equals("ADMIN")) throw new IllegalStateException("Provisioning email belongs to a non-admin account");
            return;
        }
        jdbc.update("INSERT INTO platform.users (id, name, email, password_hash, role, created_at) VALUES (?, 'Administrator', ?, ?, 'ADMIN', ?)",
                UUID.randomUUID().toString(), normalized, passwords.hash(password), Timestamp.from(Instant.now()));
    }
}

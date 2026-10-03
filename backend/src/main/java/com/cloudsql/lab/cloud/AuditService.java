package com.cloudsql.lab.cloud;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate independent;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuditService.class);
    public AuditService(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        this.jdbc = jdbc; independent = new TransactionTemplate(transactions);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public void record(String actor, String action, String target, String outcome) {
        try {
            independent.executeWithoutResult(status -> jdbc.update("INSERT INTO platform.audit_events (id, actor_id, action, target, outcome, occurred_at) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), actor, action, target, outcome, Timestamp.from(Instant.now())));
        } catch (RuntimeException exception) { log.warn("Audit event could not be persisted ({})", exception.getClass().getSimpleName()); }
    }
    public void recordInTransaction(String actor, String action, String target, String outcome) {
        jdbc.update("INSERT INTO platform.audit_events (id, actor_id, action, target, outcome, occurred_at) VALUES (?, ?, ?, ?, ?, ?)", UUID.randomUUID().toString(), actor, action, target, outcome, Timestamp.from(Instant.now()));
    }
}

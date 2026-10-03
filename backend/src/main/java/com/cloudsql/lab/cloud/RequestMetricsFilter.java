package com.cloudsql.lab.cloud;

import com.cloudsql.lab.auth.Account;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component @Order(0)
public class RequestMetricsFilter extends OncePerRequestFilter {
    private final CloudOperationsService operations;
    private final AuditService audit;
    public RequestMetricsFilter(CloudOperationsService operations, AuditService audit) { this.operations = operations; this.audit = audit; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        String path = request.getServletPath();
        if (!path.startsWith("/api/") || request.getMethod().equals("OPTIONS") || path.equals("/api/health")) { chain.doFilter(request, response); return; }
        long started = System.nanoTime(); boolean failed = false;
        try { chain.doFilter(request, response); }
        catch (IOException | ServletException | RuntimeException exception) { failed = true; throw exception; }
        finally {
            Account account = (Account) request.getAttribute("account");
            String actor = account == null ? null : account.id();
            String group = path.startsWith("/api/auth/") ? "AUTH" : path.startsWith("/api/admin/") ? "ADMIN" : path.startsWith("/api/problems") ? "PRACTICE" : path.startsWith("/api/databases") ? "WORKSPACE" : path.startsWith("/api/backups") ? "BACKUP" : "DASHBOARD";
            int status = failed ? 500 : response.getStatus();
            operations.recordRequest(actor, group, status, Math.max(1, (System.nanoTime() - started) / 1_000_000));
            if (status == 403) audit.record(actor, "ACCESS_DENIED", group, "DENIED");
            else if (status >= 400 && (group.equals("WORKSPACE") || group.equals("BACKUP"))) audit.record(actor, "RESOURCE_REQUEST", group, status == 409 ? "QUOTA_REJECTED" : "FAILED");
        }
    }
}

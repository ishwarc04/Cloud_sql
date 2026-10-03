package com.cloudsql.lab.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component @Order(1)
public class SessionFilter extends OncePerRequestFilter {
    private final AuthService auth;
    private final Set<String> origins;
    public SessionFilter(AuthService auth, @Value("${cloudsql.cors.allowed-origins}") String allowed) {
        this.auth = auth;
        origins = Arrays.stream(allowed.split(",")).map(String::trim).collect(Collectors.toSet());
        if (origins.stream().anyMatch(origin -> origin.contains("*") || !origin.matches("https?://[^/]+")))
            throw new IllegalArgumentException("CORS requires exact HTTP(S) origins");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String path = request.getServletPath();
        if (!path.startsWith("/api/")) { chain.doFilter(request, response); return; }
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        if (request.getMethod().equals("OPTIONS")) { chain.doFilter(request, response); return; }
        String origin = request.getHeader("Origin");
        if (origin != null && !origins.contains(origin)) { error(response, 403, "Origin not allowed."); return; }
        if (origin != null) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.addHeader("Vary", "Origin");
        }
        if (!Set.of("GET", "HEAD").contains(request.getMethod()) && !"1".equals(request.getHeader("X-CloudSQL-Request"))) {
            error(response, 403, "Missing request protection header."); return;
        }
        Account account = auth.resolve(token(request));
        request.setAttribute("account", account);
        boolean publicPath = path.equals("/api/health") || path.equals("/api/auth/login") || path.equals("/api/auth/signup") || path.equals("/api/auth/logout");
        if (!publicPath && account == null) { error(response, 401, "Please sign in to continue."); return; }
        if (path.startsWith("/api/admin") && !"ADMIN".equals(account.role())) { error(response, 403, "Administrator access required."); return; }
        if (account != null && "ADMIN".equals(account.role()) && (path.equals("/api/dashboard") || path.startsWith("/api/problems") || path.startsWith("/api/databases") || path.startsWith("/api/backups"))) {
            error(response, 403, "Learner access required. Use the admin workspace."); return;
        }
        chain.doFilter(request, response);
    }
    public static String token(HttpServletRequest request) {
        if (request.getCookies() != null) for (Cookie cookie : request.getCookies())
            if (cookie.getName().equals("cloudsql_session")) return cookie.getValue();
        return null;
    }
    private void error(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}

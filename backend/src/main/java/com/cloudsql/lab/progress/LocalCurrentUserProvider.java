package com.cloudsql.lab.progress;

import org.springframework.stereotype.Component;
import com.cloudsql.lab.auth.Account;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class LocalCurrentUserProvider implements CurrentUserProvider {
    private final HttpServletRequest request;
    public LocalCurrentUserProvider(HttpServletRequest request) { this.request = request; }
    @Override
    public String currentUserId() {
        Account account = (Account) request.getAttribute("account");
        if (account == null) throw new IllegalStateException("Authenticated account required");
        return account.id();
    }
}

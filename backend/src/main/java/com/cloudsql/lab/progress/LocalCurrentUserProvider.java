package com.cloudsql.lab.progress;

import org.springframework.stereotype.Component;

@Component
public class LocalCurrentUserProvider implements CurrentUserProvider {
    @Override
    public String currentUserId() {
        return "local-user";
    }
}

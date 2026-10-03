package com.cloudsql.lab.admin;

import com.cloudsql.lab.admin.dto.AdminDatabaseResponse;
import com.cloudsql.lab.admin.dto.AdminOverviewResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService service;
    private final AdminAnalyticsService analytics;

    public AdminController(AdminService service, AdminAnalyticsService analytics) {
        this.service = service;
        this.analytics = analytics;
    }

    @GetMapping("/analytics")
    public com.cloudsql.lab.admin.dto.AdminAnalyticsResponse analytics() { return analytics.snapshot(); }

    @GetMapping("/users/{id}")
    public AdminAnalyticsService.UserDetail user(@org.springframework.web.bind.annotation.PathVariable String id) { return analytics.user(id); }

    @GetMapping("/overview")
    public AdminOverviewResponse overview() {
        return service.overview();
    }

    @GetMapping("/databases")
    public List<AdminDatabaseResponse> databases() {
        return service.databases();
    }
}

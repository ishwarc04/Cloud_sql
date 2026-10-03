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
    private final AdminQuestionService questions;
    private final com.cloudsql.lab.auth.AuthService auth;
    private final com.cloudsql.lab.cloud.CloudOperationsService cloud;

    public AdminController(AdminService service, AdminAnalyticsService analytics, AdminQuestionService questions, com.cloudsql.lab.auth.AuthService auth, com.cloudsql.lab.cloud.CloudOperationsService cloud) {
        this.service = service;
        this.analytics = analytics;
        this.questions = questions; this.auth = auth;
        this.cloud = cloud;
    }
    @GetMapping("/cloud") public com.cloudsql.lab.cloud.CloudOperationsService.Report cloud() { return cloud.report(); }
    @org.springframework.web.bind.annotation.PostMapping("/cloud/probe")
    public com.cloudsql.lab.cloud.CloudOperationsService.Report probe() { cloud.probe(); return cloud.report(); }

    @org.springframework.web.bind.annotation.PostMapping("/users")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public com.cloudsql.lab.auth.Account createUser(@jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody com.cloudsql.lab.auth.AuthController.Signup input) {
        return auth.signup(input.name(), input.email(), input.password());
    }

    @org.springframework.web.bind.annotation.PostMapping("/problems")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public AdminQuestionService.PublishedQuestion createQuestion(@jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody AdminQuestionService.CreateQuestion input, jakarta.servlet.http.HttpServletRequest request) {
        return questions.create(input, ((com.cloudsql.lab.auth.Account) request.getAttribute("account")).id());
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

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

    public AdminController(AdminService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public AdminOverviewResponse overview() {
        return service.overview();
    }

    @GetMapping("/databases")
    public List<AdminDatabaseResponse> databases() {
        return service.databases();
    }
}

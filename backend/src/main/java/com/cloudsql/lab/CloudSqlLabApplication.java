package com.cloudsql.lab;

import com.cloudsql.lab.database.WorkspaceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
@EnableConfigurationProperties(WorkspaceProperties.class)
public class CloudSqlLabApplication {
    public static void main(String[] args) {
        SpringApplication.run(CloudSqlLabApplication.class, args);
    }
}

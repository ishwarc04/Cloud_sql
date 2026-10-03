package com.cloudsql.lab.cloud;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component @Profile("!test")
public class HealthSampler {
    private final CloudOperationsService operations;
    public HealthSampler(CloudOperationsService operations) { this.operations = operations; }
    @Scheduled(initialDelay=10000, fixedDelayString="${cloudsql.monitoring.interval-ms:60000}")
    public void sample() { operations.probe(); }
}

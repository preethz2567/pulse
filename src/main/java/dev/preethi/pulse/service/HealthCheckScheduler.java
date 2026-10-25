package dev.preethi.pulse.service;

import dev.preethi.pulse.entity.Monitor;
import dev.preethi.pulse.repository.MonitorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;

@Service
public class HealthCheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckScheduler.class);

    private final MonitorRepository monitorRepository;
    private final MonitorHealthCheckService healthCheckService;

    public HealthCheckScheduler(
            MonitorRepository monitorRepository,
            MonitorHealthCheckService healthCheckService
    ) {
        this.monitorRepository = monitorRepository;
        this.healthCheckService = healthCheckService;
    }

    @Scheduled(fixedDelayString = "${pulse.health-check.poll-interval-ms:5000}", initialDelayString = "${pulse.health-check.initial-delay-ms:5000}")
    public void runHealthChecks() {
        Instant now = Instant.now();
        List<Monitor> activeMonitors = monitorRepository.findByIsActiveTrue();

        List<Monitor> dueMonitors = activeMonitors.stream()
                .filter(m -> isDue(m, now))
                .toList();

        if (dueMonitors.isEmpty()) {
            return;
        }

        log.debug("Found {} due monitors to check", dueMonitors.size());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Monitor monitor : dueMonitors) {
                Long monitorId = monitor.getId();
                executor.submit(() -> {
                    try {
                        healthCheckService.checkMonitor(monitorId);
                    } catch (Exception e) {
                        log.error("Error executing health check for monitor ID {}", monitorId, e);
                    }
                });
            }
        }
    }

    public boolean isDue(Monitor monitor, Instant now) {
        if (!Boolean.TRUE.equals(monitor.getIsActive())) {
            return false;
        }
        if (monitor.getLastCheckedAt() == null) {
            return true;
        }
        int intervalSeconds = monitor.getIntervalSeconds() != null ? monitor.getIntervalSeconds() : 60;
        return !monitor.getLastCheckedAt().plusSeconds(intervalSeconds).isAfter(now);
    }
}

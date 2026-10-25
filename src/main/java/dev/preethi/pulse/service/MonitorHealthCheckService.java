package dev.preethi.pulse.service;

import dev.preethi.pulse.entity.CheckResult;
import dev.preethi.pulse.entity.Incident;
import dev.preethi.pulse.entity.Monitor;
import dev.preethi.pulse.exception.SsrfProtectionException;
import dev.preethi.pulse.repository.CheckResultRepository;
import dev.preethi.pulse.repository.IncidentRepository;
import dev.preethi.pulse.repository.MonitorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class MonitorHealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(MonitorHealthCheckService.class);

    private final MonitorRepository monitorRepository;
    private final CheckResultRepository checkResultRepository;
    private final IncidentRepository incidentRepository;
    private final EmailAlertService emailAlertService;
    private final SsrfProtectionService ssrfProtectionService;
    private final HttpClient httpClient;

    public MonitorHealthCheckService(
            MonitorRepository monitorRepository,
            CheckResultRepository checkResultRepository,
            IncidentRepository incidentRepository,
            EmailAlertService emailAlertService,
            SsrfProtectionService ssrfProtectionService,
            HttpClient httpClient
    ) {
        this.monitorRepository = monitorRepository;
        this.checkResultRepository = checkResultRepository;
        this.incidentRepository = incidentRepository;
        this.emailAlertService = emailAlertService;
        this.ssrfProtectionService = ssrfProtectionService;
        this.httpClient = httpClient;
    }

    @Transactional
    public CheckResult checkMonitor(Long monitorId) {
        Monitor monitor = monitorRepository.findById(monitorId).orElse(null);
        if (monitor == null || !Boolean.TRUE.equals(monitor.getIsActive())) {
            return null;
        }
        return performCheck(monitor);
    }

    @Transactional
    public CheckResult performCheck(Monitor monitor) {
        Instant checkTime = Instant.now();
        long startTime = System.currentTimeMillis();

        Integer statusCode = null;
        Integer responseTimeMs = null;
        boolean isSuccess = false;
        String errorMessage = null;

        try {
            // 1. SSRF validation
            ssrfProtectionService.validateUrl(monitor.getUrl());

            // 2. Build HTTP request
            int timeoutSec = (monitor.getTimeoutSeconds() != null) ? monitor.getTimeoutSeconds() : 30;
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(monitor.getUrl()))
                    .timeout(Duration.ofSeconds(timeoutSec))
                    .header("User-Agent", "Pulse-Monitor/1.0");

            String method = (monitor.getHttpMethod() != null) ? monitor.getHttpMethod().toUpperCase() : "GET";
            switch (method) {
                case "GET" -> requestBuilder.GET();
                case "HEAD" -> requestBuilder.method("HEAD", HttpRequest.BodyPublishers.noBody());
                case "POST" -> requestBuilder.POST(HttpRequest.BodyPublishers.noBody());
                case "PUT" -> requestBuilder.PUT(HttpRequest.BodyPublishers.noBody());
                case "DELETE" -> requestBuilder.DELETE();
                default -> requestBuilder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            HttpRequest request = requestBuilder.build();

            // 3. Send request
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            responseTimeMs = (int) (System.currentTimeMillis() - startTime);
            statusCode = response.statusCode();

            int expectedStatus = (monitor.getExpectedStatusCode() != null) ? monitor.getExpectedStatusCode() : 200;
            if (statusCode == expectedStatus) {
                isSuccess = true;
                monitor.setStatus("UP");
            } else {
                isSuccess = false;
                errorMessage = "Expected status " + expectedStatus + " but received " + statusCode;
                monitor.setStatus("DOWN");
            }

        } catch (SsrfProtectionException e) {
            responseTimeMs = (int) (System.currentTimeMillis() - startTime);
            isSuccess = false;
            errorMessage = "SSRF Protection: " + e.getMessage();
            monitor.setStatus("DOWN");
            log.warn("Monitor ID {} blocked by SSRF protection: {}", monitor.getId(), e.getMessage());
        } catch (HttpTimeoutException e) {
            responseTimeMs = (int) (System.currentTimeMillis() - startTime);
            isSuccess = false;
            errorMessage = "Request timed out: " + e.getMessage();
            monitor.setStatus("DOWN");
        } catch (IOException e) {
            responseTimeMs = (int) (System.currentTimeMillis() - startTime);
            isSuccess = false;
            errorMessage = "Connection error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            monitor.setStatus("DOWN");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            responseTimeMs = (int) (System.currentTimeMillis() - startTime);
            isSuccess = false;
            errorMessage = "Check interrupted";
            monitor.setStatus("DOWN");
        } catch (Exception e) {
            responseTimeMs = (int) (System.currentTimeMillis() - startTime);
            isSuccess = false;
            errorMessage = "Unexpected error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            monitor.setStatus("DOWN");
        }

        // Always update lastCheckedAt
        monitor.setLastCheckedAt(checkTime);
        monitorRepository.save(monitor);

        CheckResult result = new CheckResult(monitor, statusCode, responseTimeMs, isSuccess, errorMessage);
        result = checkResultRepository.save(result);

        handleIncidentLifecycle(monitor, isSuccess, errorMessage, checkTime);

        return result;
    }

    private void handleIncidentLifecycle(Monitor monitor, boolean isSuccess, String failureCause, Instant checkTime) {
        if (monitor.getId() == null) {
            return;
        }

        if (isSuccess) {
            incidentRepository.findActiveIncidentByMonitorId(monitor.getId()).ifPresent(activeIncident -> {
                activeIncident.resolve(checkTime);
                incidentRepository.save(activeIncident);
                log.info("Resolved active incident {} for monitor ID {}", activeIncident.getId(), monitor.getId());
                try {
                    emailAlertService.sendIncidentResolvedAlert(activeIncident);
                } catch (Exception e) {
                    log.error("Error triggering incident resolved alert for monitor ID {}: {}", monitor.getId(), e.getMessage());
                }
            });
        } else {
            Optional<Incident> activeIncidentOpt = incidentRepository.findActiveIncidentByMonitorId(monitor.getId());
            if (activeIncidentOpt.isEmpty()) {
                String cause = (failureCause != null && !failureCause.isBlank()) ? failureCause : "Health check failed";
                Incident incident = new Incident(monitor, Incident.STATUS_ONGOING, cause, checkTime);
                incidentRepository.save(incident);
                log.info("Created new incident for monitor ID {} due to: {}", monitor.getId(), cause);
                try {
                    emailAlertService.sendIncidentOpenedAlert(incident);
                } catch (Exception e) {
                    log.error("Error triggering incident opened alert for monitor ID {}: {}", monitor.getId(), e.getMessage());
                }
            }
        }
    }
}

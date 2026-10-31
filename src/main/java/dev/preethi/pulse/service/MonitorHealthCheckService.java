package dev.preethi.pulse.service;

import dev.preethi.pulse.entity.CheckResult;
import dev.preethi.pulse.entity.Incident;
import dev.preethi.pulse.entity.Monitor;
import dev.preethi.pulse.exception.SsrfProtectionException;
import dev.preethi.pulse.repository.CheckResultRepository;
import dev.preethi.pulse.repository.IncidentRepository;
import dev.preethi.pulse.repository.MonitorRepository;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.SocketTimeoutException;
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
    private final CloseableHttpClient httpClient;

    public MonitorHealthCheckService(
            MonitorRepository monitorRepository,
            CheckResultRepository checkResultRepository,
            IncidentRepository incidentRepository,
            EmailAlertService emailAlertService,
            SsrfProtectionService ssrfProtectionService,
            CloseableHttpClient httpClient
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
            // 1. Pre-execution SSRF URL validation
            ssrfProtectionService.validateUrl(monitor.getUrl());

            // 2. Build HTTP request with configured timeout
            int timeoutSec = (monitor.getTimeoutSeconds() != null) ? monitor.getTimeoutSeconds() : 30;
            RequestConfig requestConfig = RequestConfig.custom()
                    .setResponseTimeout(Timeout.ofSeconds(timeoutSec))
                    .setConnectionRequestTimeout(Timeout.ofSeconds(timeoutSec))
                    .build();

            HttpClientContext context = HttpClientContext.create();
            context.setRequestConfig(requestConfig);

            String method = (monitor.getHttpMethod() != null) ? monitor.getHttpMethod().toUpperCase() : "GET";
            ClassicRequestBuilder requestBuilder = ClassicRequestBuilder.create(method)
                    .setUri(monitor.getUrl())
                    .setHeader("User-Agent", "Pulse-Monitor/1.0");

            ClassicHttpRequest request = requestBuilder.build();

            // 3. Execute request via Apache HC5 (DnsResolver enforces SSRF safety at connection time)
            statusCode = httpClient.execute(request, context, response -> {
                EntityUtils.consumeQuietly(response.getEntity());
                return response.getCode();
            });

            responseTimeMs = (int) (System.currentTimeMillis() - startTime);

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
        } catch (SocketTimeoutException e) {
            responseTimeMs = (int) (System.currentTimeMillis() - startTime);
            isSuccess = false;
            errorMessage = "Request timed out: " + (e.getMessage() != null ? e.getMessage() : "Timeout");
            monitor.setStatus("DOWN");
        } catch (IOException e) {
            responseTimeMs = (int) (System.currentTimeMillis() - startTime);
            isSuccess = false;
            Throwable cause = e.getCause();
            if ((e.getMessage() != null && e.getMessage().contains("SSRF Protection")) ||
                (cause != null && cause.getMessage() != null && cause.getMessage().contains("SSRF Protection"))) {
                String msg = (cause != null && cause.getMessage() != null && cause.getMessage().contains("SSRF Protection"))
                        ? cause.getMessage()
                        : e.getMessage();
                errorMessage = msg.startsWith("SSRF Protection") ? msg : "SSRF Protection: " + msg;
                log.warn("Monitor ID {} blocked by SSRF-safe DNS resolver: {}", monitor.getId(), msg);
            } else {
                errorMessage = "Connection error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
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

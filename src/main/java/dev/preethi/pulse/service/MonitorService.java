package dev.preethi.pulse.service;

import dev.preethi.pulse.dto.CheckResultResponse;
import dev.preethi.pulse.dto.CreateMonitorRequest;
import dev.preethi.pulse.dto.MonitorResponse;
import dev.preethi.pulse.dto.UpdateMonitorRequest;
import dev.preethi.pulse.dto.UptimeResponse;
import dev.preethi.pulse.dto.UptimeStatsProjection;
import dev.preethi.pulse.entity.Monitor;
import dev.preethi.pulse.entity.User;
import dev.preethi.pulse.exception.MonitorNotFoundException;
import dev.preethi.pulse.repository.CheckResultRepository;
import dev.preethi.pulse.repository.MonitorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class MonitorService {

    private final MonitorRepository monitorRepository;
    private final CheckResultRepository checkResultRepository;
    private final UserService userService;
    private final Clock clock;

    @Autowired
    public MonitorService(
            MonitorRepository monitorRepository,
            CheckResultRepository checkResultRepository,
            UserService userService
    ) {
        this(monitorRepository, checkResultRepository, userService, Clock.systemUTC());
    }

    public MonitorService(
            MonitorRepository monitorRepository,
            CheckResultRepository checkResultRepository,
            UserService userService,
            Clock clock
    ) {
        this.monitorRepository = monitorRepository;
        this.checkResultRepository = checkResultRepository;
        this.userService = userService;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Transactional
    public MonitorResponse createMonitor(String userEmail, CreateMonitorRequest request) {
        User user = userService.getUserByEmail(userEmail);

        Monitor monitor = new Monitor(user, request.name(), request.url());
        monitor.setHttpMethod(request.httpMethod());
        monitor.setIntervalSeconds(request.intervalSeconds());
        monitor.setTimeoutSeconds(request.timeoutSeconds());
        monitor.setExpectedStatusCode(request.expectedStatusCode());
        monitor.setIsActive(request.isActive());
        monitor.setStatus(Boolean.TRUE.equals(request.isActive()) ? "PENDING" : "PAUSED");

        Monitor saved = monitorRepository.save(monitor);
        return MonitorResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<MonitorResponse> getUserMonitors(String userEmail) {
        User user = userService.getUserByEmail(userEmail);
        return monitorRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(MonitorResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MonitorResponse getMonitor(Long id, String userEmail) {
        User user = userService.getUserByEmail(userEmail);
        Monitor monitor = monitorRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new MonitorNotFoundException("Monitor not found with id: " + id));
        return MonitorResponse.from(monitor);
    }

    @Transactional
    public MonitorResponse updateMonitor(Long id, String userEmail, UpdateMonitorRequest request) {
        User user = userService.getUserByEmail(userEmail);
        Monitor monitor = monitorRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new MonitorNotFoundException("Monitor not found with id: " + id));

        monitor.setName(request.name());
        monitor.setUrl(request.url());
        monitor.setHttpMethod(request.httpMethod());
        monitor.setIntervalSeconds(request.intervalSeconds());
        monitor.setTimeoutSeconds(request.timeoutSeconds());
        monitor.setExpectedStatusCode(request.expectedStatusCode());

        if (Boolean.FALSE.equals(request.isActive())) {
            monitor.setIsActive(false);
            monitor.setStatus("PAUSED");
        } else {
            monitor.setIsActive(true);
            if ("PAUSED".equals(monitor.getStatus())) {
                monitor.setStatus("PENDING");
            }
        }

        Monitor updated = monitorRepository.save(monitor);
        return MonitorResponse.from(updated);
    }

    @Transactional
    public void deleteMonitor(Long id, String userEmail) {
        User user = userService.getUserByEmail(userEmail);
        Monitor monitor = monitorRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new MonitorNotFoundException("Monitor not found with id: " + id));
        monitorRepository.delete(monitor);
    }

    @Transactional(readOnly = true)
    public List<CheckResultResponse> getMonitorResults(Long id, String userEmail, int limit) {
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("Limit must be between 1 and 500");
        }

        User user = userService.getUserByEmail(userEmail);
        Monitor monitor = monitorRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new MonitorNotFoundException("Monitor not found with id: " + id));

        PageRequest pageRequest = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "checkedAt"));
        return checkResultRepository.findByMonitorId(monitor.getId(), pageRequest)
                .stream()
                .map(CheckResultResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UptimeResponse getMonitorUptime(Long id, String userEmail, String range) {
        if (range == null || range.isBlank()) {
            throw new IllegalArgumentException("Range must not be null or blank");
        }

        Duration duration = switch (range) {
            case "24h" -> Duration.ofHours(24);
            case "7d" -> Duration.ofDays(7);
            case "30d" -> Duration.ofDays(30);
            default -> throw new IllegalArgumentException("Unsupported range: " + range + ". Supported ranges are: 24h, 7d, 30d");
        };

        User user = userService.getUserByEmail(userEmail);
        Monitor monitor = monitorRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new MonitorNotFoundException("Monitor not found with id: " + id));

        Instant now = Instant.now(clock);
        Instant since = now.minus(duration);

        UptimeStatsProjection stats = checkResultRepository.findUptimeStats(monitor.getId(), since, now);

        long totalChecks = stats != null ? stats.getTotalChecks() : 0L;
        long successfulChecks = stats != null ? stats.getSuccessfulChecks() : 0L;
        long failedChecks = totalChecks - successfulChecks;

        double uptimePercentage = 0.0;
        if (totalChecks > 0) {
            uptimePercentage = (successfulChecks * 100.0) / totalChecks;
        }

        return new UptimeResponse(
                monitor.getId(),
                range,
                uptimePercentage,
                totalChecks,
                successfulChecks,
                failedChecks,
                since,
                now
        );
    }
}

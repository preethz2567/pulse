package dev.preethi.pulse.service;

import dev.preethi.pulse.dto.CreateMonitorRequest;
import dev.preethi.pulse.dto.MonitorResponse;
import dev.preethi.pulse.dto.UpdateMonitorRequest;
import dev.preethi.pulse.entity.Monitor;
import dev.preethi.pulse.entity.User;
import dev.preethi.pulse.exception.MonitorNotFoundException;
import dev.preethi.pulse.repository.MonitorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MonitorService {

    private final MonitorRepository monitorRepository;
    private final UserService userService;

    public MonitorService(MonitorRepository monitorRepository, UserService userService) {
        this.monitorRepository = monitorRepository;
        this.userService = userService;
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
}

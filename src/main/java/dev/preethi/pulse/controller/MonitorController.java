package dev.preethi.pulse.controller;

import dev.preethi.pulse.dto.CheckResultResponse;
import dev.preethi.pulse.dto.CreateMonitorRequest;
import dev.preethi.pulse.dto.MonitorResponse;
import dev.preethi.pulse.dto.UpdateMonitorRequest;
import dev.preethi.pulse.dto.UptimeResponse;
import dev.preethi.pulse.service.MonitorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/monitors")
public class MonitorController {

    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @PostMapping
    public ResponseEntity<MonitorResponse> createMonitor(
            @Valid @RequestBody CreateMonitorRequest request,
            Principal principal
    ) {
        MonitorResponse response = monitorService.createMonitor(principal.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<MonitorResponse>> listMonitors(Principal principal) {
        List<MonitorResponse> monitors = monitorService.getUserMonitors(principal.getName());
        return ResponseEntity.ok(monitors);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MonitorResponse> getMonitor(
            @PathVariable Long id,
            Principal principal
    ) {
        MonitorResponse monitor = monitorService.getMonitor(id, principal.getName());
        return ResponseEntity.ok(monitor);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MonitorResponse> updateMonitor(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMonitorRequest request,
            Principal principal
    ) {
        MonitorResponse monitor = monitorService.updateMonitor(id, principal.getName(), request);
        return ResponseEntity.ok(monitor);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMonitor(
            @PathVariable Long id,
            Principal principal
    ) {
        monitorService.deleteMonitor(id, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/results")
    public ResponseEntity<List<CheckResultResponse>> getMonitorResults(
            @PathVariable Long id,
            @RequestParam(defaultValue = "50") int limit,
            Principal principal
    ) {
        List<CheckResultResponse> results = monitorService.getMonitorResults(id, principal.getName(), limit);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/{id}/uptime")
    public ResponseEntity<UptimeResponse> getMonitorUptime(
            @PathVariable Long id,
            @RequestParam(name = "range", required = false) String range,
            Principal principal
    ) {
        UptimeResponse uptime = monitorService.getMonitorUptime(id, principal.getName(), range);
        return ResponseEntity.ok(uptime);
    }
}

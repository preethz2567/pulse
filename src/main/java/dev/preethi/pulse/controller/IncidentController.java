package dev.preethi.pulse.controller;

import dev.preethi.pulse.dto.IncidentResponse;
import dev.preethi.pulse.service.IncidentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping
    public ResponseEntity<List<IncidentResponse>> listIncidents(
            @RequestParam(required = false) String status,
            Principal principal
    ) {
        List<IncidentResponse> incidents = incidentService.getIncidents(principal.getName(), status);
        return ResponseEntity.ok(incidents);
    }

    @GetMapping("/{id}")
    public ResponseEntity<IncidentResponse> getIncident(
            @PathVariable Long id,
            Principal principal
    ) {
        IncidentResponse incident = incidentService.getIncident(id, principal.getName());
        return ResponseEntity.ok(incident);
    }

    @PatchMapping("/{id}/acknowledge")
    public ResponseEntity<IncidentResponse> acknowledgeIncident(
            @PathVariable Long id,
            Principal principal
    ) {
        IncidentResponse response = incidentService.acknowledgeIncident(id, principal.getName());
        return ResponseEntity.ok(response);
    }
}

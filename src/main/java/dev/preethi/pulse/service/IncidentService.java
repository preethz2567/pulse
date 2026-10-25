package dev.preethi.pulse.service;

import dev.preethi.pulse.dto.IncidentResponse;
import dev.preethi.pulse.entity.Incident;
import dev.preethi.pulse.entity.User;
import dev.preethi.pulse.exception.IncidentNotFoundException;
import dev.preethi.pulse.exception.InvalidIncidentStateException;
import dev.preethi.pulse.exception.InvalidIncidentStatusException;
import dev.preethi.pulse.repository.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class IncidentService {

    private static final Set<String> VALID_STATUSES = Set.of(
            Incident.STATUS_ONGOING,
            Incident.STATUS_ACKNOWLEDGED,
            Incident.STATUS_RESOLVED
    );

    private final IncidentRepository incidentRepository;
    private final UserService userService;

    public IncidentService(IncidentRepository incidentRepository, UserService userService) {
        this.incidentRepository = incidentRepository;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> getIncidents(String userEmail, String status) {
        User user = userService.getUserByEmail(userEmail);

        if (status != null && !status.isBlank()) {
            String normalizedStatus = status.trim().toUpperCase();
            if (!VALID_STATUSES.contains(normalizedStatus)) {
                throw new InvalidIncidentStatusException(
                        "Invalid incident status: " + status + ". Allowed values: ONGOING, ACKNOWLEDGED, RESOLVED"
                );
            }
            return incidentRepository.findByMonitorUserIdAndStatusOrderByStartedAtDesc(user.getId(), normalizedStatus)
                    .stream()
                    .map(IncidentResponse::from)
                    .toList();
        }

        return incidentRepository.findByMonitorUserIdOrderByStartedAtDesc(user.getId())
                .stream()
                .map(IncidentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public IncidentResponse getIncident(Long id, String userEmail) {
        User user = userService.getUserByEmail(userEmail);

        Incident incident = incidentRepository.findByIdAndMonitorUserId(id, user.getId())
                .orElseThrow(() -> new IncidentNotFoundException("Incident not found with id: " + id));

        return IncidentResponse.from(incident);
    }

    @Transactional
    public IncidentResponse acknowledgeIncident(Long id, String userEmail) {
        User user = userService.getUserByEmail(userEmail);

        Incident incident = incidentRepository.findByIdAndMonitorUserId(id, user.getId())
                .orElseThrow(() -> new IncidentNotFoundException("Incident not found with id: " + id));

        if (!Incident.STATUS_ONGOING.equals(incident.getStatus())) {
            throw new InvalidIncidentStateException(
                    "Cannot acknowledge incident with status: " + incident.getStatus() + ". Only ONGOING incidents can be acknowledged."
            );
        }

        incident.acknowledge(Instant.now());
        Incident saved = incidentRepository.save(incident);
        return IncidentResponse.from(saved);
    }
}

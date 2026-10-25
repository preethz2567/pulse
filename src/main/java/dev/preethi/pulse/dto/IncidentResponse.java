package dev.preethi.pulse.dto;

import dev.preethi.pulse.entity.Incident;

import java.time.Instant;

public record IncidentResponse(
        Long id,
        Long monitorId,
        String monitorName,
        String status,
        String cause,
        Instant startedAt,
        Instant acknowledgedAt,
        Instant resolvedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static IncidentResponse from(Incident incident) {
        return new IncidentResponse(
                incident.getId(),
                incident.getMonitor() != null ? incident.getMonitor().getId() : null,
                incident.getMonitor() != null ? incident.getMonitor().getName() : null,
                incident.getStatus(),
                incident.getCause(),
                incident.getStartedAt(),
                incident.getAcknowledgedAt(),
                incident.getResolvedAt(),
                incident.getCreatedAt(),
                incident.getUpdatedAt()
        );
    }
}

package dev.preethi.pulse.dto;

import dev.preethi.pulse.entity.Incident;

import java.time.Instant;

public record PublicIncidentResponse(
        Long id,
        Long monitorId,
        String monitorName,
        String status,
        String cause,
        Instant startedAt,
        Instant acknowledgedAt,
        Instant resolvedAt
) {
    public static PublicIncidentResponse from(Incident incident) {
        String sanitizedCause = sanitizeCause(incident.getCause());
        return new PublicIncidentResponse(
                incident.getId(),
                incident.getMonitor() != null ? incident.getMonitor().getId() : null,
                incident.getMonitor() != null ? incident.getMonitor().getName() : null,
                incident.getStatus(),
                sanitizedCause,
                incident.getStartedAt(),
                incident.getAcknowledgedAt(),
                incident.getResolvedAt()
        );
    }

    private static String sanitizeCause(String cause) {
        if (cause == null || cause.isBlank()) {
            return "Service disruption";
        }
        if (cause.contains("SSRF Protection")) {
            return "Connection policy violation";
        }
        if (cause.contains("http://") || cause.contains("https://") || cause.matches(".*\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b.*")) {
            if (cause.startsWith("Connection error")) {
                return "Connection error";
            }
            if (cause.startsWith("Request timed out")) {
                return "Request timed out";
            }
            return "Service disruption";
        }
        if (cause.startsWith("Unexpected error")) {
            return "Service disruption";
        }
        return cause;
    }
}

package dev.preethi.pulse.dto;

import dev.preethi.pulse.entity.Monitor;

import java.time.Instant;

public record PublicMonitorStatusResponse(
        Long id,
        String name,
        String status,
        Instant lastCheckedAt
) {
    public static PublicMonitorStatusResponse from(Monitor monitor) {
        return new PublicMonitorStatusResponse(
                monitor.getId(),
                monitor.getName(),
                monitor.getStatus(),
                monitor.getLastCheckedAt()
        );
    }
}

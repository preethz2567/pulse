package dev.preethi.pulse.dto;

import dev.preethi.pulse.entity.Monitor;

import java.time.Instant;

public record MonitorResponse(
    Long id,
    Long userId,
    String name,
    String url,
    String httpMethod,
    int intervalSeconds,
    int timeoutSeconds,
    int expectedStatusCode,
    String status,
    boolean isActive,
    Instant lastCheckedAt,
    Instant createdAt,
    Instant updatedAt
) {
    public static MonitorResponse from(Monitor monitor) {
        return new MonitorResponse(
            monitor.getId(),
            monitor.getUser().getId(),
            monitor.getName(),
            monitor.getUrl(),
            monitor.getHttpMethod(),
            monitor.getIntervalSeconds(),
            monitor.getTimeoutSeconds(),
            monitor.getExpectedStatusCode(),
            monitor.getStatus(),
            monitor.getIsActive(),
            monitor.getLastCheckedAt(),
            monitor.getCreatedAt(),
            monitor.getUpdatedAt()
        );
    }
}

package dev.preethi.pulse.dto;

import dev.preethi.pulse.entity.Monitor;
import dev.preethi.pulse.entity.StatusPage;

import java.time.Instant;
import java.util.List;

public record StatusPageResponse(
        Long id,
        Long userId,
        String name,
        String slug,
        String description,
        boolean isPublished,
        List<Long> monitorIds,
        Instant createdAt,
        Instant updatedAt
) {
    public static StatusPageResponse from(StatusPage statusPage) {
        List<Long> monitorIds = statusPage.getMonitors() != null
                ? statusPage.getMonitors().stream().map(Monitor::getId).toList()
                : List.of();

        return new StatusPageResponse(
                statusPage.getId(),
                statusPage.getUser().getId(),
                statusPage.getName(),
                statusPage.getSlug(),
                statusPage.getDescription(),
                Boolean.TRUE.equals(statusPage.getIsPublished()),
                monitorIds,
                statusPage.getCreatedAt(),
                statusPage.getUpdatedAt()
        );
    }
}

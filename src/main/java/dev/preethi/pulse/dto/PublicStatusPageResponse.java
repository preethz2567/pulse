package dev.preethi.pulse.dto;

import java.time.Instant;
import java.util.List;

public record PublicStatusPageResponse(
        String name,
        String description,
        String overallStatus,
        List<PublicMonitorStatusResponse> monitors,
        List<PublicIncidentResponse> incidents,
        Instant updatedAt
) {
}

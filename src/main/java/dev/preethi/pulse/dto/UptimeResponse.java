package dev.preethi.pulse.dto;

import java.time.Instant;

public record UptimeResponse(
        Long monitorId,
        String range,
        Double uptimePercentage,
        Long totalChecks,
        Long successfulChecks,
        Long failedChecks,
        Instant periodStart,
        Instant periodEnd
) {
}

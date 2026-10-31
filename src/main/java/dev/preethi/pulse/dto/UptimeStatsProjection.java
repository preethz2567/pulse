package dev.preethi.pulse.dto;

public interface UptimeStatsProjection {
    long getTotalChecks();
    long getSuccessfulChecks();
}

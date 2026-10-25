package dev.preethi.pulse.dto;

import dev.preethi.pulse.entity.AlertChannel;

import java.time.Instant;

public record AlertChannelResponse(
        Long id,
        Long userId,
        Long monitorId,
        String name,
        String type,
        String destination,
        boolean isEnabled,
        Instant createdAt,
        Instant updatedAt
) {
    public static AlertChannelResponse from(AlertChannel channel) {
        return new AlertChannelResponse(
                channel.getId(),
                channel.getUser().getId(),
                channel.getMonitor() != null ? channel.getMonitor().getId() : null,
                channel.getName(),
                channel.getType(),
                channel.getDestination(),
                Boolean.TRUE.equals(channel.getIsEnabled()),
                channel.getCreatedAt(),
                channel.getUpdatedAt()
        );
    }
}

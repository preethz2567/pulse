package dev.preethi.pulse.dto;

public record LoginResponse(
    String accessToken,
    String tokenType,
    UserResponse user
) {
}

package dev.preethi.pulse.dto;

import dev.preethi.pulse.entity.User;

public record UserResponse(
    Long id,
    String email,
    String role
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole());
    }
}

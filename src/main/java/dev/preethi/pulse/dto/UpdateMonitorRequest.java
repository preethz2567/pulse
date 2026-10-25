package dev.preethi.pulse.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateMonitorRequest(
    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    String name,

    @NotBlank(message = "URL is required")
    @Size(max = 2048, message = "URL must not exceed 2048 characters")
    @Pattern(regexp = "^https?://.*", message = "URL must start with http:// or https://")
    String url,

    @Pattern(regexp = "^(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)$", message = "HTTP method must be valid")
    String httpMethod,

    @Min(value = 10, message = "Interval must be at least 10 seconds")
    Integer intervalSeconds,

    @Min(value = 1, message = "Timeout must be at least 1 second")
    @Max(value = 120, message = "Timeout must not exceed 120 seconds")
    Integer timeoutSeconds,

    @Min(value = 100, message = "Expected status code must be a valid HTTP status")
    @Max(value = 599, message = "Expected status code must be a valid HTTP status")
    Integer expectedStatusCode,

    Boolean isActive
) {
    public UpdateMonitorRequest {
        if (httpMethod == null || httpMethod.isBlank()) {
            httpMethod = "GET";
        }
        if (intervalSeconds == null) {
            intervalSeconds = 60;
        }
        if (timeoutSeconds == null) {
            timeoutSeconds = 30;
        }
        if (expectedStatusCode == null) {
            expectedStatusCode = 200;
        }
        if (isActive == null) {
            isActive = true;
        }
    }
}

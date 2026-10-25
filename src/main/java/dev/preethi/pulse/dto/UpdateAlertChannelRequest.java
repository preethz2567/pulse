package dev.preethi.pulse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAlertChannelRequest(
        @NotBlank(message = "Channel name is required")
        @Size(max = 255, message = "Channel name must not exceed 255 characters")
        String name,

        @NotBlank(message = "Channel type is required")
        @Pattern(regexp = "^(?i)(EMAIL|WEBHOOK)$", message = "Type must be EMAIL or WEBHOOK")
        String type,

        @NotBlank(message = "Destination is required")
        @Size(max = 255, message = "Destination must not exceed 255 characters")
        String destination,

        Boolean isEnabled
) {
    public UpdateAlertChannelRequest {
        if (type == null || type.isBlank()) {
            type = "EMAIL";
        } else {
            type = type.toUpperCase();
        }
        if (isEnabled == null) {
            isEnabled = true;
        }
    }
}

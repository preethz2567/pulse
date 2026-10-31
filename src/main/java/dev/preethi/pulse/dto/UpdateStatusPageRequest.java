package dev.preethi.pulse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Collections;
import java.util.List;

public record UpdateStatusPageRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must not exceed 255 characters")
        String name,

        @NotBlank(message = "Slug is required")
        @Size(max = 100, message = "Slug must not exceed 100 characters")
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Slug must contain only lowercase alphanumeric characters and hyphens")
        String slug,

        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,

        Boolean isPublished,

        List<Long> monitorIds
) {
    public UpdateStatusPageRequest {
        if (isPublished == null) {
            isPublished = false;
        }
        if (monitorIds == null) {
            monitorIds = Collections.emptyList();
        }
    }
}

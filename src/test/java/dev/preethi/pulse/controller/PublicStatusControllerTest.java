package dev.preethi.pulse.controller;

import dev.preethi.pulse.config.JwtAuthenticationEntryPoint;
import dev.preethi.pulse.config.JwtAuthenticationFilter;
import dev.preethi.pulse.config.SecurityConfig;
import dev.preethi.pulse.dto.PublicIncidentResponse;
import dev.preethi.pulse.dto.PublicMonitorStatusResponse;
import dev.preethi.pulse.dto.PublicStatusPageResponse;
import dev.preethi.pulse.exception.GlobalExceptionHandler;
import dev.preethi.pulse.exception.StatusPageNotFoundException;
import dev.preethi.pulse.service.JwtService;
import dev.preethi.pulse.service.PublicStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicStatusController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class PublicStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PublicStatusService publicStatusService;

    @MockBean
    private JwtService jwtService;

    @Test
    void getPublicStatus_whenPublished_returns200WithoutAuthentication() throws Exception {
        PublicMonitorStatusResponse monitor = new PublicMonitorStatusResponse(
                10L,
                "API Gateway",
                "UP",
                Instant.parse("2026-10-31T12:00:00Z")
        );

        PublicIncidentResponse incident = new PublicIncidentResponse(
                100L,
                10L,
                "API Gateway",
                "ONGOING",
                "High error rates",
                Instant.parse("2026-10-31T11:50:00Z"),
                null,
                null
        );

        PublicStatusPageResponse response = new PublicStatusPageResponse(
                "Acme Status",
                "Public status of Acme services",
                "UP",
                List.of(monitor),
                List.of(incident),
                Instant.parse("2026-10-31T12:00:00Z")
        );

        when(publicStatusService.getPublicStatus("acme")).thenReturn(response);

        mockMvc.perform(get("/api/status/acme"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=30, public"))
                .andExpect(jsonPath("$.name").value("Acme Status"))
                .andExpect(jsonPath("$.description").value("Public status of Acme services"))
                .andExpect(jsonPath("$.overallStatus").value("UP"))
                .andExpect(jsonPath("$.monitors.length()").value(1))
                .andExpect(jsonPath("$.monitors[0].id").value(10))
                .andExpect(jsonPath("$.monitors[0].name").value("API Gateway"))
                .andExpect(jsonPath("$.monitors[0].status").value("UP"))
                .andExpect(jsonPath("$.incidents.length()").value(1))
                .andExpect(jsonPath("$.incidents[0].id").value(100))
                .andExpect(jsonPath("$.incidents[0].status").value("ONGOING"))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-31T12:00:00Z"));

        verify(publicStatusService).getPublicStatus("acme");
    }

    @Test
    void getPublicStatus_whenUnpublished_returns404WithoutAuthentication() throws Exception {
        when(publicStatusService.getPublicStatus("draft"))
                .thenThrow(new StatusPageNotFoundException("Status page not found with slug: draft"));

        mockMvc.perform(get("/api/status/draft"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Status page not found with slug: draft"));
    }

    @Test
    void getPublicStatus_whenUnknownSlug_returns404WithoutAuthentication() throws Exception {
        when(publicStatusService.getPublicStatus("unknown-slug"))
                .thenThrow(new StatusPageNotFoundException("Status page not found with slug: unknown-slug"));

        mockMvc.perform(get("/api/status/unknown-slug"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void getPublicStatus_neverExposesMonitorUrlsOrUserInfo() throws Exception {
        PublicMonitorStatusResponse monitor = new PublicMonitorStatusResponse(
                10L,
                "Billing Service",
                "UP",
                Instant.parse("2026-10-31T12:00:00Z")
        );

        PublicStatusPageResponse response = new PublicStatusPageResponse(
                "Acme Status",
                "All systems operational",
                "UP",
                List.of(monitor),
                List.of(),
                Instant.parse("2026-10-31T12:00:00Z")
        );

        when(publicStatusService.getPublicStatus("acme")).thenReturn(response);

        mockMvc.perform(get("/api/status/acme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monitors[0].url").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.userEmail").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(content().string(not(containsString("http://"))))
                .andExpect(content().string(not(containsString("https://"))))
                .andExpect(content().string(not(containsString("email"))));
    }

    @Test
    void unauthenticatedPostToStatusEndpoint_returns401Unauthorized() throws Exception {
        mockMvc.perform(post("/api/status/acme"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}

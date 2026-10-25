package dev.preethi.pulse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.preethi.pulse.config.JwtAuthenticationEntryPoint;
import dev.preethi.pulse.config.JwtAuthenticationFilter;
import dev.preethi.pulse.config.SecurityConfig;
import dev.preethi.pulse.dto.AlertChannelResponse;
import dev.preethi.pulse.dto.CreateAlertChannelRequest;
import dev.preethi.pulse.dto.UpdateAlertChannelRequest;
import dev.preethi.pulse.exception.AlertChannelNotFoundException;
import dev.preethi.pulse.exception.GlobalExceptionHandler;
import dev.preethi.pulse.exception.MonitorNotFoundException;
import dev.preethi.pulse.service.AlertChannelService;
import dev.preethi.pulse.service.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AlertChannelController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class AlertChannelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AlertChannelService alertChannelService;

    @MockBean
    private JwtService jwtService;

    private static final String VALID_TOKEN = "valid.jwt.token";

    @BeforeEach
    void setUp() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("alice@example.com");
        when(claims.get("role", String.class)).thenReturn("ROLE_USER");
        when(jwtService.extractAllClaims(VALID_TOKEN)).thenReturn(claims);
    }

    @Test
    void createAlertChannel_whenAuthenticatedAndValid_returns201() throws Exception {
        CreateAlertChannelRequest request = new CreateAlertChannelRequest(
                10L,
                "Slack Webhook",
                "WEBHOOK",
                "https://hooks.slack.com/services/123",
                true
        );

        AlertChannelResponse response = new AlertChannelResponse(
                100L,
                1L,
                10L,
                "Slack Webhook",
                "WEBHOOK",
                "https://hooks.slack.com/services/123",
                true,
                Instant.now(),
                Instant.now()
        );

        when(alertChannelService.createAlertChannel(eq("alice@example.com"), any(CreateAlertChannelRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/alert-channels")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.monitorId").value(10))
                .andExpect(jsonPath("$.name").value("Slack Webhook"))
                .andExpect(jsonPath("$.type").value("WEBHOOK"))
                .andExpect(jsonPath("$.destination").value("https://hooks.slack.com/services/123"))
                .andExpect(jsonPath("$.isEnabled").value(true));
    }

    @Test
    void createAlertChannel_whenUnauthenticated_returns401() throws Exception {
        CreateAlertChannelRequest request = new CreateAlertChannelRequest(
                10L,
                "Slack Webhook",
                "WEBHOOK",
                "https://hooks.slack.com/services/123",
                true
        );

        mockMvc.perform(post("/api/alert-channels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(alertChannelService);
    }

    @Test
    void createAlertChannel_whenInvalidPayload_returns400() throws Exception {
        // Missing name and destination
        String invalidJson = """
                {
                    "monitorId": 10,
                    "name": "",
                    "type": "INVALID_TYPE",
                    "destination": ""
                }
                """;

        mockMvc.perform(post("/api/alert-channels")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));

        verifyNoInteractions(alertChannelService);
    }

    @Test
    void createAlertChannel_whenMonitorNotFoundOrNotOwned_returns404() throws Exception {
        CreateAlertChannelRequest request = new CreateAlertChannelRequest(
                999L,
                "Email Channel",
                "EMAIL",
                "ops@example.com",
                true
        );

        when(alertChannelService.createAlertChannel(eq("alice@example.com"), any(CreateAlertChannelRequest.class)))
                .thenThrow(new MonitorNotFoundException("Monitor not found with id: 999"));

        mockMvc.perform(post("/api/alert-channels")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Monitor not found with id: 999"));
    }

    @Test
    void listAlertChannels_whenAuthenticated_returns200() throws Exception {
        AlertChannelResponse r1 = new AlertChannelResponse(
                100L, 1L, 10L, "Channel 1", "EMAIL", "a@example.com", true, Instant.now(), Instant.now()
        );
        AlertChannelResponse r2 = new AlertChannelResponse(
                101L, 1L, 10L, "Channel 2", "WEBHOOK", "https://hook.example.com", true, Instant.now(), Instant.now()
        );

        when(alertChannelService.getAlertChannels("alice@example.com", null))
                .thenReturn(List.of(r1, r2));

        mockMvc.perform(get("/api/alert-channels")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Channel 1"))
                .andExpect(jsonPath("$[1].name").value("Channel 2"));
    }

    @Test
    void listAlertChannels_withMonitorId_returnsFilteredList() throws Exception {
        AlertChannelResponse r1 = new AlertChannelResponse(
                100L, 1L, 10L, "Channel 1", "EMAIL", "a@example.com", true, Instant.now(), Instant.now()
        );

        when(alertChannelService.getAlertChannels("alice@example.com", 10L))
                .thenReturn(List.of(r1));

        mockMvc.perform(get("/api/alert-channels?monitorId=10")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(100));
    }

    @Test
    void listAlertChannels_whenUnauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/alert-channels"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(alertChannelService);
    }

    @Test
    void getAlertChannel_whenFoundAndOwned_returns200() throws Exception {
        AlertChannelResponse response = new AlertChannelResponse(
                100L, 1L, 10L, "Email Channel", "EMAIL", "ops@example.com", true, Instant.now(), Instant.now()
        );

        when(alertChannelService.getAlertChannel(100L, "alice@example.com"))
                .thenReturn(response);

        mockMvc.perform(get("/api/alert-channels/100")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.name").value("Email Channel"));
    }

    @Test
    void getAlertChannel_whenNotFoundOrNotOwned_returns404() throws Exception {
        when(alertChannelService.getAlertChannel(999L, "alice@example.com"))
                .thenThrow(new AlertChannelNotFoundException("Alert channel not found with id: 999"));

        mockMvc.perform(get("/api/alert-channels/999")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Alert channel not found with id: 999"));
    }

    @Test
    void updateAlertChannel_whenValidAndOwned_returns200() throws Exception {
        UpdateAlertChannelRequest request = new UpdateAlertChannelRequest(
                "Updated Channel",
                "WEBHOOK",
                "https://example.com/updated",
                false
        );

        AlertChannelResponse response = new AlertChannelResponse(
                100L, 1L, 10L, "Updated Channel", "WEBHOOK", "https://example.com/updated", false, Instant.now(), Instant.now()
        );

        when(alertChannelService.updateAlertChannel(eq(100L), eq("alice@example.com"), any(UpdateAlertChannelRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/alert-channels/100")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Channel"))
                .andExpect(jsonPath("$.type").value("WEBHOOK"))
                .andExpect(jsonPath("$.isEnabled").value(false));
    }

    @Test
    void updateAlertChannel_whenNotFoundOrNotOwned_returns404() throws Exception {
        UpdateAlertChannelRequest request = new UpdateAlertChannelRequest(
                "Updated Channel",
                "EMAIL",
                "ops@example.com",
                true
        );

        when(alertChannelService.updateAlertChannel(eq(999L), eq("alice@example.com"), any(UpdateAlertChannelRequest.class)))
                .thenThrow(new AlertChannelNotFoundException("Alert channel not found with id: 999"));

        mockMvc.perform(put("/api/alert-channels/999")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void deleteAlertChannel_whenOwned_returns204() throws Exception {
        doNothing().when(alertChannelService).deleteAlertChannel(100L, "alice@example.com");

        mockMvc.perform(delete("/api/alert-channels/100")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNoContent());

        verify(alertChannelService).deleteAlertChannel(100L, "alice@example.com");
    }

    @Test
    void deleteAlertChannel_whenNotFoundOrNotOwned_returns404() throws Exception {
        doThrow(new AlertChannelNotFoundException("Alert channel not found with id: 999"))
                .when(alertChannelService).deleteAlertChannel(999L, "alice@example.com");

        mockMvc.perform(delete("/api/alert-channels/999")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void deleteAlertChannel_whenUnauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/alert-channels/100"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(alertChannelService);
    }
}

package dev.preethi.pulse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.preethi.pulse.config.JwtAuthenticationEntryPoint;
import dev.preethi.pulse.config.JwtAuthenticationFilter;
import dev.preethi.pulse.config.SecurityConfig;
import dev.preethi.pulse.dto.CreateMonitorRequest;
import dev.preethi.pulse.dto.MonitorResponse;
import dev.preethi.pulse.dto.UpdateMonitorRequest;
import dev.preethi.pulse.exception.GlobalExceptionHandler;
import dev.preethi.pulse.exception.MonitorNotFoundException;
import dev.preethi.pulse.service.JwtService;
import dev.preethi.pulse.service.MonitorService;
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

@WebMvcTest(MonitorController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class MonitorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MonitorService monitorService;

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
    void createMonitor_whenAuthenticatedAndValid_returns201() throws Exception {
        CreateMonitorRequest request = new CreateMonitorRequest(
                "Production API",
                "https://api.example.com/health",
                "GET",
                60,
                30,
                200,
                true
        );

        MonitorResponse response = new MonitorResponse(
                1L,
                10L,
                "Production API",
                "https://api.example.com/health",
                "GET",
                60,
                30,
                200,
                "PENDING",
                true,
                null,
                Instant.now(),
                Instant.now()
        );

        when(monitorService.createMonitor(eq("alice@example.com"), any(CreateMonitorRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/monitors")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Production API"))
                .andExpect(jsonPath("$.url").value("https://api.example.com/health"))
                .andExpect(jsonPath("$.intervalSeconds").value(60))
                .andExpect(jsonPath("$.timeoutSeconds").value(30))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.isActive").value(true));

        verify(monitorService).createMonitor(eq("alice@example.com"), any(CreateMonitorRequest.class));
    }

    @Test
    void createMonitor_withInvalidUrl_returns400() throws Exception {
        CreateMonitorRequest request = new CreateMonitorRequest(
                "Invalid URL Monitor",
                "not-a-valid-url",
                "GET",
                60,
                30,
                200,
                true
        );

        mockMvc.perform(post("/api/monitors")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));

        verifyNoInteractions(monitorService);
    }

    @Test
    void createMonitor_withIntervalBelowMinimum_returns400() throws Exception {
        CreateMonitorRequest request = new CreateMonitorRequest(
                "Short Interval Monitor",
                "https://example.com",
                "GET",
                5, // below minimum 10
                30,
                200,
                true
        );

        mockMvc.perform(post("/api/monitors")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(monitorService);
    }

    @Test
    void createMonitor_withTimeoutExceedingMaximum_returns400() throws Exception {
        CreateMonitorRequest request = new CreateMonitorRequest(
                "Long Timeout Monitor",
                "https://example.com",
                "GET",
                60,
                150, // exceeds max 120
                200,
                true
        );

        mockMvc.perform(post("/api/monitors")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(monitorService);
    }

    @Test
    void createMonitor_whenUnauthenticated_returns401() throws Exception {
        CreateMonitorRequest request = new CreateMonitorRequest(
                "Unauth Monitor",
                "https://example.com",
                "GET",
                60,
                30,
                200,
                true
        );

        mockMvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(monitorService);
    }

    @Test
    void listMonitors_whenAuthenticated_returns200() throws Exception {
        MonitorResponse monitor = new MonitorResponse(
                1L, 10L, "App 1", "https://app1.com", "GET", 60, 30, 200, "UP", true, null, Instant.now(), Instant.now()
        );

        when(monitorService.getUserMonitors("alice@example.com")).thenReturn(List.of(monitor));

        mockMvc.perform(get("/api/monitors")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].name").value("App 1"));

        verify(monitorService).getUserMonitors("alice@example.com");
    }

    @Test
    void listMonitors_whenUnauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/monitors"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(monitorService);
    }

    @Test
    void getMonitor_whenOwned_returns200() throws Exception {
        MonitorResponse monitor = new MonitorResponse(
                1L, 10L, "App 1", "https://app1.com", "GET", 60, 30, 200, "UP", true, null, Instant.now(), Instant.now()
        );

        when(monitorService.getMonitor(1L, "alice@example.com")).thenReturn(monitor);

        mockMvc.perform(get("/api/monitors/1")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("App 1"));

        verify(monitorService).getMonitor(1L, "alice@example.com");
    }

    @Test
    void getMonitor_whenNotFoundOrUnowned_returns404() throws Exception {
        when(monitorService.getMonitor(999L, "alice@example.com"))
                .thenThrow(new MonitorNotFoundException("Monitor not found with id: 999"));

        mockMvc.perform(get("/api/monitors/999")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Monitor not found with id: 999"));

        verify(monitorService).getMonitor(999L, "alice@example.com");
    }

    @Test
    void getMonitor_whenUnauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/monitors/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(monitorService);
    }

    @Test
    void updateMonitor_whenOwnedAndValid_returns200() throws Exception {
        UpdateMonitorRequest request = new UpdateMonitorRequest(
                "Updated App",
                "https://updated.com",
                "POST",
                120,
                45,
                200,
                true
        );

        MonitorResponse response = new MonitorResponse(
                1L, 10L, "Updated App", "https://updated.com", "POST", 120, 45, 200, "UP", true, null, Instant.now(), Instant.now()
        );

        when(monitorService.updateMonitor(eq(1L), eq("alice@example.com"), any(UpdateMonitorRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/monitors/1")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Updated App"))
                .andExpect(jsonPath("$.intervalSeconds").value(120));

        verify(monitorService).updateMonitor(eq(1L), eq("alice@example.com"), any(UpdateMonitorRequest.class));
    }

    @Test
    void updateMonitor_whenInvalidInput_returns400() throws Exception {
        UpdateMonitorRequest request = new UpdateMonitorRequest(
                "", // Blank name
                "https://updated.com",
                "POST",
                120,
                45,
                200,
                true
        );

        mockMvc.perform(put("/api/monitors/1")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(monitorService);
    }

    @Test
    void updateMonitor_whenNotFoundOrUnowned_returns404() throws Exception {
        UpdateMonitorRequest request = new UpdateMonitorRequest(
                "Valid Name",
                "https://valid.com",
                "GET",
                60,
                30,
                200,
                true
        );

        when(monitorService.updateMonitor(eq(999L), eq("alice@example.com"), any(UpdateMonitorRequest.class)))
                .thenThrow(new MonitorNotFoundException("Monitor not found with id: 999"));

        mockMvc.perform(put("/api/monitors/999")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        verify(monitorService).updateMonitor(eq(999L), eq("alice@example.com"), any(UpdateMonitorRequest.class));
    }

    @Test
    void updateMonitor_whenUnauthenticated_returns401() throws Exception {
        UpdateMonitorRequest request = new UpdateMonitorRequest(
                "Valid Name",
                "https://valid.com",
                "GET",
                60,
                30,
                200,
                true
        );

        mockMvc.perform(put("/api/monitors/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(monitorService);
    }

    @Test
    void deleteMonitor_whenOwned_returns204() throws Exception {
        doNothing().when(monitorService).deleteMonitor(1L, "alice@example.com");

        mockMvc.perform(delete("/api/monitors/1")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNoContent());

        verify(monitorService).deleteMonitor(1L, "alice@example.com");
    }

    @Test
    void deleteMonitor_whenNotFoundOrUnowned_returns404() throws Exception {
        doThrow(new MonitorNotFoundException("Monitor not found with id: 999"))
                .when(monitorService).deleteMonitor(999L, "alice@example.com");

        mockMvc.perform(delete("/api/monitors/999")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));

        verify(monitorService).deleteMonitor(999L, "alice@example.com");
    }

    @Test
    void deleteMonitor_whenUnauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/monitors/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(monitorService);
    }
}

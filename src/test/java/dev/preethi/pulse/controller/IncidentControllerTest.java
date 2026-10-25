package dev.preethi.pulse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.preethi.pulse.config.JwtAuthenticationEntryPoint;
import dev.preethi.pulse.config.JwtAuthenticationFilter;
import dev.preethi.pulse.config.SecurityConfig;
import dev.preethi.pulse.dto.IncidentResponse;
import dev.preethi.pulse.exception.GlobalExceptionHandler;
import dev.preethi.pulse.exception.IncidentNotFoundException;
import dev.preethi.pulse.exception.InvalidIncidentStateException;
import dev.preethi.pulse.exception.InvalidIncidentStatusException;
import dev.preethi.pulse.service.IncidentService;
import dev.preethi.pulse.service.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IncidentController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IncidentService incidentService;

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
    void listIncidents_whenAuthenticated_returns200() throws Exception {
        IncidentResponse r1 = new IncidentResponse(
                100L, 10L, "Auth API", "ONGOING", "500 Internal Error",
                Instant.now().minusSeconds(120), null, null, Instant.now().minusSeconds(120), Instant.now().minusSeconds(120)
        );

        when(incidentService.getIncidents("alice@example.com", null))
                .thenReturn(List.of(r1));

        mockMvc.perform(get("/api/incidents")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(100))
                .andExpect(jsonPath("$[0].monitorId").value(10))
                .andExpect(jsonPath("$[0].monitorName").value("Auth API"))
                .andExpect(jsonPath("$[0].status").value("ONGOING"));
    }

    @Test
    void listIncidents_withValidStatusFilter_returns200() throws Exception {
        IncidentResponse r1 = new IncidentResponse(
                100L, 10L, "Auth API", "ONGOING", "500 Internal Error",
                Instant.now().minusSeconds(120), null, null, Instant.now().minusSeconds(120), Instant.now().minusSeconds(120)
        );

        when(incidentService.getIncidents("alice@example.com", "ONGOING"))
                .thenReturn(List.of(r1));

        mockMvc.perform(get("/api/incidents?status=ONGOING")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("ONGOING"));
    }

    @Test
    void listIncidents_withInvalidStatusFilter_returns400() throws Exception {
        when(incidentService.getIncidents("alice@example.com", "INVALID_STATUS"))
                .thenThrow(new InvalidIncidentStatusException("Invalid incident status: INVALID_STATUS. Allowed values: ONGOING, ACKNOWLEDGED, RESOLVED"));

        mockMvc.perform(get("/api/incidents?status=INVALID_STATUS")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid incident status: INVALID_STATUS. Allowed values: ONGOING, ACKNOWLEDGED, RESOLVED"));
    }

    @Test
    void listIncidents_whenUnauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/incidents"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(incidentService);
    }

    @Test
    void getIncident_whenFoundAndOwned_returns200() throws Exception {
        IncidentResponse response = new IncidentResponse(
                100L, 10L, "Auth API", "ONGOING", "500 Internal Error",
                Instant.now().minusSeconds(120), null, null, Instant.now().minusSeconds(120), Instant.now().minusSeconds(120)
        );

        when(incidentService.getIncident(100L, "alice@example.com"))
                .thenReturn(response);

        mockMvc.perform(get("/api/incidents/100")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("ONGOING"))
                .andExpect(jsonPath("$.monitorName").value("Auth API"));
    }

    @Test
    void getIncident_whenNotFoundOrNotOwned_returns404() throws Exception {
        when(incidentService.getIncident(999L, "alice@example.com"))
                .thenThrow(new IncidentNotFoundException("Incident not found with id: 999"));

        mockMvc.perform(get("/api/incidents/999")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Incident not found with id: 999"));
    }

    @Test
    void getIncident_whenUnauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/incidents/100"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(incidentService);
    }

    @Test
    void acknowledgeIncident_whenOngoingAndOwned_returns200() throws Exception {
        IncidentResponse response = new IncidentResponse(
                100L, 10L, "Auth API", "ACKNOWLEDGED", "500 Internal Error",
                Instant.now().minusSeconds(120), Instant.now(), null, Instant.now().minusSeconds(120), Instant.now()
        );

        when(incidentService.acknowledgeIncident(100L, "alice@example.com"))
                .thenReturn(response);

        mockMvc.perform(patch("/api/incidents/100/acknowledge")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.acknowledgedAt").isNotEmpty());
    }

    @Test
    void acknowledgeIncident_whenNotFoundOrNotOwned_returns404() throws Exception {
        when(incidentService.acknowledgeIncident(999L, "alice@example.com"))
                .thenThrow(new IncidentNotFoundException("Incident not found with id: 999"));

        mockMvc.perform(patch("/api/incidents/999/acknowledge")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void acknowledgeIncident_whenAlreadyAcknowledgedOrResolved_returns400() throws Exception {
        when(incidentService.acknowledgeIncident(100L, "alice@example.com"))
                .thenThrow(new InvalidIncidentStateException("Cannot acknowledge incident with status: ACKNOWLEDGED. Only ONGOING incidents can be acknowledged."));

        mockMvc.perform(patch("/api/incidents/100/acknowledge")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Cannot acknowledge incident with status: ACKNOWLEDGED. Only ONGOING incidents can be acknowledged."));
    }

    @Test
    void acknowledgeIncident_whenUnauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/incidents/100/acknowledge"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(incidentService);
    }
}

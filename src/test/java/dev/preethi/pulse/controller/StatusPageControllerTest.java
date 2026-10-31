package dev.preethi.pulse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.preethi.pulse.config.JwtAuthenticationEntryPoint;
import dev.preethi.pulse.config.JwtAuthenticationFilter;
import dev.preethi.pulse.config.SecurityConfig;
import dev.preethi.pulse.dto.CreateStatusPageRequest;
import dev.preethi.pulse.dto.StatusPageResponse;
import dev.preethi.pulse.dto.UpdateStatusPageRequest;
import dev.preethi.pulse.exception.DuplicateSlugException;
import dev.preethi.pulse.exception.GlobalExceptionHandler;
import dev.preethi.pulse.exception.MonitorNotFoundException;
import dev.preethi.pulse.exception.StatusPageNotFoundException;
import dev.preethi.pulse.service.JwtService;
import dev.preethi.pulse.service.StatusPageService;
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
import java.util.Collections;
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

@WebMvcTest(StatusPageController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class StatusPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StatusPageService statusPageService;

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
    void createStatusPage_whenAuthenticatedAndValid_returns201() throws Exception {
        CreateStatusPageRequest request = new CreateStatusPageRequest(
                "Acme Status",
                "acme",
                "Acme status page",
                true,
                List.of(10L, 20L)
        );

        StatusPageResponse response = new StatusPageResponse(
                1L,
                10L,
                "Acme Status",
                "acme",
                "Acme status page",
                true,
                List.of(10L, 20L),
                Instant.now(),
                Instant.now()
        );

        when(statusPageService.createStatusPage(eq("alice@example.com"), any(CreateStatusPageRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/status-pages")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Acme Status"))
                .andExpect(jsonPath("$.slug").value("acme"))
                .andExpect(jsonPath("$.isPublished").value(true))
                .andExpect(jsonPath("$.monitorIds.length()").value(2));

        verify(statusPageService).createStatusPage(eq("alice@example.com"), any(CreateStatusPageRequest.class));
    }

    @Test
    void createStatusPage_withEmptyMonitorList_returns201() throws Exception {
        CreateStatusPageRequest request = new CreateStatusPageRequest(
                "Acme Empty",
                "acme-empty",
                null,
                false,
                Collections.emptyList()
        );

        StatusPageResponse response = new StatusPageResponse(
                2L,
                10L,
                "Acme Empty",
                "acme-empty",
                null,
                false,
                Collections.emptyList(),
                Instant.now(),
                Instant.now()
        );

        when(statusPageService.createStatusPage(eq("alice@example.com"), any(CreateStatusPageRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/status-pages")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("acme-empty"))
                .andExpect(jsonPath("$.monitorIds.length()").value(0));
    }

    @Test
    void createStatusPage_withDuplicateSlug_returns409Conflict() throws Exception {
        CreateStatusPageRequest request = new CreateStatusPageRequest(
                "Acme Duplicate",
                "acme",
                null,
                true,
                List.of()
        );

        when(statusPageService.createStatusPage(eq("alice@example.com"), any(CreateStatusPageRequest.class)))
                .thenThrow(new DuplicateSlugException("Status page with slug already exists: acme"));

        mockMvc.perform(post("/api/status-pages")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Status page with slug already exists: acme"));
    }

    @Test
    void createStatusPage_withBlankName_returns400BadRequest() throws Exception {
        CreateStatusPageRequest request = new CreateStatusPageRequest(
                "",
                "valid-slug",
                null,
                true,
                List.of()
        );

        mockMvc.perform(post("/api/status-pages")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(statusPageService);
    }

    @Test
    void createStatusPage_withInvalidSlugFormat_returns400BadRequest() throws Exception {
        CreateStatusPageRequest request = new CreateStatusPageRequest(
                "Valid Name",
                "Invalid Slug With Spaces",
                null,
                true,
                List.of()
        );

        mockMvc.perform(post("/api/status-pages")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(statusPageService);
    }

    @Test
    void createStatusPage_withUnownedOrNonExistentMonitor_returns404NotFound() throws Exception {
        CreateStatusPageRequest request = new CreateStatusPageRequest(
                "Valid Name",
                "valid-slug",
                null,
                true,
                List.of(999L)
        );

        when(statusPageService.createStatusPage(eq("alice@example.com"), any(CreateStatusPageRequest.class)))
                .thenThrow(new MonitorNotFoundException("Monitor not found with id: 999"));

        mockMvc.perform(post("/api/status-pages")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Monitor not found with id: 999"));
    }

    @Test
    void createStatusPage_whenUnauthenticated_returns401() throws Exception {
        CreateStatusPageRequest request = new CreateStatusPageRequest(
                "Unauth Status",
                "unauth",
                null,
                false,
                List.of()
        );

        mockMvc.perform(post("/api/status-pages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(statusPageService);
    }

    @Test
    void getUserStatusPages_whenAuthenticated_returns200() throws Exception {
        StatusPageResponse response = new StatusPageResponse(
                1L,
                10L,
                "Acme Status",
                "acme",
                "Acme status page",
                true,
                List.of(10L),
                Instant.now(),
                Instant.now()
        );

        when(statusPageService.getUserStatusPages("alice@example.com")).thenReturn(List.of(response));

        mockMvc.perform(get("/api/status-pages")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].slug").value("acme"));

        verify(statusPageService).getUserStatusPages("alice@example.com");
    }

    @Test
    void getUserStatusPages_whenUnauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/status-pages"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(statusPageService);
    }

    @Test
    void getStatusPage_whenOwned_returns200() throws Exception {
        StatusPageResponse response = new StatusPageResponse(
                1L,
                10L,
                "Acme Status",
                "acme",
                "Acme status page",
                true,
                List.of(10L),
                Instant.now(),
                Instant.now()
        );

        when(statusPageService.getStatusPage(1L, "alice@example.com")).thenReturn(response);

        mockMvc.perform(get("/api/status-pages/1")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Acme Status"))
                .andExpect(jsonPath("$.slug").value("acme"));

        verify(statusPageService).getStatusPage(1L, "alice@example.com");
    }

    @Test
    void getStatusPage_whenNotFoundOrUnowned_returns404() throws Exception {
        when(statusPageService.getStatusPage(999L, "alice@example.com"))
                .thenThrow(new StatusPageNotFoundException("Status page not found with id: 999"));

        mockMvc.perform(get("/api/status-pages/999")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Status page not found with id: 999"));
    }

    @Test
    void getStatusPage_whenUnauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/status-pages/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(statusPageService);
    }

    @Test
    void updateStatusPage_whenOwnedAndValid_returns200() throws Exception {
        UpdateStatusPageRequest request = new UpdateStatusPageRequest(
                "Acme Status Updated",
                "acme-updated",
                "New description",
                true,
                List.of(20L)
        );

        StatusPageResponse response = new StatusPageResponse(
                1L,
                10L,
                "Acme Status Updated",
                "acme-updated",
                "New description",
                true,
                List.of(20L),
                Instant.now(),
                Instant.now()
        );

        when(statusPageService.updateStatusPage(eq(1L), eq("alice@example.com"), any(UpdateStatusPageRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/status-pages/1")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Acme Status Updated"))
                .andExpect(jsonPath("$.slug").value("acme-updated"));

        verify(statusPageService).updateStatusPage(eq(1L), eq("alice@example.com"), any(UpdateStatusPageRequest.class));
    }

    @Test
    void updateStatusPage_withDuplicateSlug_returns409Conflict() throws Exception {
        UpdateStatusPageRequest request = new UpdateStatusPageRequest(
                "Acme Page",
                "taken-slug",
                null,
                true,
                List.of()
        );

        when(statusPageService.updateStatusPage(eq(1L), eq("alice@example.com"), any(UpdateStatusPageRequest.class)))
                .thenThrow(new DuplicateSlugException("Status page with slug already exists: taken-slug"));

        mockMvc.perform(put("/api/status-pages/1")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Status page with slug already exists: taken-slug"));
    }

    @Test
    void updateStatusPage_withBlankNameOrInvalidSlug_returns400BadRequest() throws Exception {
        UpdateStatusPageRequest request = new UpdateStatusPageRequest(
                "",
                "valid-slug",
                null,
                true,
                List.of()
        );

        mockMvc.perform(put("/api/status-pages/1")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(statusPageService);
    }

    @Test
    void updateStatusPage_withUnownedOrNonExistentMonitor_returns404NotFound() throws Exception {
        UpdateStatusPageRequest request = new UpdateStatusPageRequest(
                "Valid Name",
                "valid-slug",
                null,
                true,
                List.of(999L)
        );

        when(statusPageService.updateStatusPage(eq(1L), eq("alice@example.com"), any(UpdateStatusPageRequest.class)))
                .thenThrow(new MonitorNotFoundException("Monitor not found with id: 999"));

        mockMvc.perform(put("/api/status-pages/1")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Monitor not found with id: 999"));
    }

    @Test
    void updateStatusPage_whenNotFoundOrUnowned_returns404() throws Exception {
        UpdateStatusPageRequest request = new UpdateStatusPageRequest(
                "Acme Page",
                "valid-slug",
                null,
                true,
                List.of()
        );

        when(statusPageService.updateStatusPage(eq(999L), eq("alice@example.com"), any(UpdateStatusPageRequest.class)))
                .thenThrow(new StatusPageNotFoundException("Status page not found with id: 999"));

        mockMvc.perform(put("/api/status-pages/999")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Status page not found with id: 999"));
    }

    @Test
    void updateStatusPage_whenUnauthenticated_returns401() throws Exception {
        UpdateStatusPageRequest request = new UpdateStatusPageRequest(
                "Acme Page",
                "valid-slug",
                null,
                true,
                List.of()
        );

        mockMvc.perform(put("/api/status-pages/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(statusPageService);
    }

    @Test
    void deleteStatusPage_whenOwned_returns204() throws Exception {
        doNothing().when(statusPageService).deleteStatusPage(1L, "alice@example.com");

        mockMvc.perform(delete("/api/status-pages/1")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNoContent());

        verify(statusPageService).deleteStatusPage(1L, "alice@example.com");
    }

    @Test
    void deleteStatusPage_whenNotFoundOrUnowned_returns404() throws Exception {
        doThrow(new StatusPageNotFoundException("Status page not found with id: 999"))
                .when(statusPageService).deleteStatusPage(999L, "alice@example.com");

        mockMvc.perform(delete("/api/status-pages/999")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Status page not found with id: 999"));

        verify(statusPageService).deleteStatusPage(999L, "alice@example.com");
    }

    @Test
    void deleteStatusPage_whenUnauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/status-pages/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(statusPageService);
    }
}

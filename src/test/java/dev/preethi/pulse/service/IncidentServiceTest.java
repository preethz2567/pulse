package dev.preethi.pulse.service;

import dev.preethi.pulse.dto.IncidentResponse;
import dev.preethi.pulse.entity.Incident;
import dev.preethi.pulse.entity.Monitor;
import dev.preethi.pulse.entity.User;
import dev.preethi.pulse.exception.IncidentNotFoundException;
import dev.preethi.pulse.exception.InvalidIncidentStateException;
import dev.preethi.pulse.exception.InvalidIncidentStatusException;
import dev.preethi.pulse.repository.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private IncidentService incidentService;

    private User user;
    private Monitor monitor;
    private Incident ongoingIncident;
    private Incident acknowledgedIncident;
    private Incident resolvedIncident;

    @BeforeEach
    void setUp() {
        user = new User("alice@example.com", "hash", "ROLE_USER");
        user.setId(1L);

        monitor = new Monitor(user, "API Service", "https://api.example.com");
        monitor.setId(10L);

        ongoingIncident = new Incident(monitor, Incident.STATUS_ONGOING, "500 Internal Server Error", Instant.now().minusSeconds(300));
        ongoingIncident.setId(100L);

        acknowledgedIncident = new Incident(monitor, Incident.STATUS_ACKNOWLEDGED, "Timeout", Instant.now().minusSeconds(600));
        acknowledgedIncident.setId(101L);
        acknowledgedIncident.setAcknowledgedAt(Instant.now().minusSeconds(200));

        resolvedIncident = new Incident(monitor, Incident.STATUS_RESOLVED, "Connection refused", Instant.now().minusSeconds(1200));
        resolvedIncident.setId(102L);
        resolvedIncident.setResolvedAt(Instant.now().minusSeconds(600));
    }

    @Test
    void getIncidents_withoutStatusFilter_returnsAllUserIncidents() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user);
        when(incidentRepository.findByMonitorUserIdOrderByStartedAtDesc(1L))
                .thenReturn(List.of(ongoingIncident, acknowledgedIncident, resolvedIncident));

        List<IncidentResponse> results = incidentService.getIncidents("alice@example.com", null);

        assertEquals(3, results.size());
        assertEquals(100L, results.get(0).id());
        assertEquals("API Service", results.get(0).monitorName());
        assertEquals("ONGOING", results.get(0).status());
        verify(incidentRepository).findByMonitorUserIdOrderByStartedAtDesc(1L);
    }

    @Test
    void getIncidents_withValidStatusFilter_returnsFilteredIncidents() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user);
        when(incidentRepository.findByMonitorUserIdAndStatusOrderByStartedAtDesc(1L, "ONGOING"))
                .thenReturn(List.of(ongoingIncident));

        List<IncidentResponse> results = incidentService.getIncidents("alice@example.com", "ongoing");

        assertEquals(1, results.size());
        assertEquals(100L, results.get(0).id());
        assertEquals("ONGOING", results.get(0).status());
        verify(incidentRepository).findByMonitorUserIdAndStatusOrderByStartedAtDesc(1L, "ONGOING");
    }

    @Test
    void getIncidents_withInvalidStatusFilter_throwsInvalidIncidentStatusException() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user);

        assertThrows(InvalidIncidentStatusException.class, () ->
                incidentService.getIncidents("alice@example.com", "UNKNOWN_STATUS")
        );

        verify(incidentRepository, never()).findByMonitorUserIdAndStatusOrderByStartedAtDesc(any(), any());
    }

    @Test
    void getIncident_whenFoundAndOwned_returnsIncidentResponse() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user);
        when(incidentRepository.findByIdAndMonitorUserId(100L, 1L))
                .thenReturn(Optional.of(ongoingIncident));

        IncidentResponse response = incidentService.getIncident(100L, "alice@example.com");

        assertNotNull(response);
        assertEquals(100L, response.id());
        assertEquals(10L, response.monitorId());
        assertEquals("API Service", response.monitorName());
        assertEquals("ONGOING", response.status());
        assertEquals("500 Internal Server Error", response.cause());
        assertNull(response.acknowledgedAt());
        assertNull(response.resolvedAt());
    }

    @Test
    void getIncident_whenNotFoundOrNotOwned_throwsIncidentNotFoundException() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user);
        when(incidentRepository.findByIdAndMonitorUserId(999L, 1L))
                .thenReturn(Optional.empty());

        assertThrows(IncidentNotFoundException.class, () ->
                incidentService.getIncident(999L, "alice@example.com")
        );
    }

    @Test
    void acknowledgeIncident_whenOngoingAndOwned_acknowledgesSuccessfully() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user);
        when(incidentRepository.findByIdAndMonitorUserId(100L, 1L))
                .thenReturn(Optional.of(ongoingIncident));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(i -> i.getArgument(0));

        IncidentResponse response = incidentService.acknowledgeIncident(100L, "alice@example.com");

        assertNotNull(response);
        assertEquals("ACKNOWLEDGED", response.status());
        assertNotNull(response.acknowledgedAt());
        assertNull(response.resolvedAt());
        verify(incidentRepository).save(ongoingIncident);
    }

    @Test
    void acknowledgeIncident_whenNotFoundOrNotOwned_throwsIncidentNotFoundException() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user);
        when(incidentRepository.findByIdAndMonitorUserId(999L, 1L))
                .thenReturn(Optional.empty());

        assertThrows(IncidentNotFoundException.class, () ->
                incidentService.acknowledgeIncident(999L, "alice@example.com")
        );

        verify(incidentRepository, never()).save(any());
    }

    @Test
    void acknowledgeIncident_whenAlreadyAcknowledged_throwsInvalidIncidentStateException() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user);
        when(incidentRepository.findByIdAndMonitorUserId(101L, 1L))
                .thenReturn(Optional.of(acknowledgedIncident));

        assertThrows(InvalidIncidentStateException.class, () ->
                incidentService.acknowledgeIncident(101L, "alice@example.com")
        );

        verify(incidentRepository, never()).save(any());
    }

    @Test
    void acknowledgeIncident_whenAlreadyResolved_throwsInvalidIncidentStateException() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user);
        when(incidentRepository.findByIdAndMonitorUserId(102L, 1L))
                .thenReturn(Optional.of(resolvedIncident));

        assertThrows(InvalidIncidentStateException.class, () ->
                incidentService.acknowledgeIncident(102L, "alice@example.com")
        );

        verify(incidentRepository, never()).save(any());
    }
}

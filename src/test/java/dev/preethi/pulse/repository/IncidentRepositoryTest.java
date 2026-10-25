package dev.preethi.pulse.repository;

import dev.preethi.pulse.entity.Incident;
import dev.preethi.pulse.entity.Monitor;
import dev.preethi.pulse.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentRepositoryTest {

    @Mock
    private IncidentRepository incidentRepository;

    private User user;
    private Monitor monitor;
    private Incident activeIncident;
    private Incident resolvedIncident;

    @BeforeEach
    void setUp() {
        user = new User("alice@example.com", "hash", "ROLE_USER");
        user.setId(1L);

        monitor = new Monitor(user, "Auth Service", "https://auth.example.com");
        monitor.setId(10L);

        activeIncident = new Incident(monitor, "ONGOING", "500 Internal Server Error", Instant.now().minusSeconds(300));
        activeIncident.setId(101L);

        resolvedIncident = new Incident(monitor, "RESOLVED", "Connection timeout", Instant.now().minusSeconds(3600));
        resolvedIncident.setId(100L);
        resolvedIncident.setResolvedAt(Instant.now().minusSeconds(1800));
    }

    @Test
    void findActiveIncidentByMonitorId_returnsOngoingOrAcknowledgedIncident() {
        when(incidentRepository.findActiveIncidentByMonitorId(10L))
                .thenReturn(Optional.of(activeIncident));

        Optional<Incident> result = incidentRepository.findActiveIncidentByMonitorId(10L);

        assertTrue(result.isPresent());
        assertEquals(101L, result.get().getId());
        assertEquals("ONGOING", result.get().getStatus());
        assertTrue(result.get().isOpen());
        verify(incidentRepository).findActiveIncidentByMonitorId(10L);
    }

    @Test
    void findActiveIncidentByMonitorId_whenNoneActive_returnsEmpty() {
        when(incidentRepository.findActiveIncidentByMonitorId(10L))
                .thenReturn(Optional.empty());

        Optional<Incident> result = incidentRepository.findActiveIncidentByMonitorId(10L);

        assertFalse(result.isPresent());
        verify(incidentRepository).findActiveIncidentByMonitorId(10L);
    }

    @Test
    void findByMonitorIdOrderByStartedAtDesc_retrievesNewestFirst() {
        when(incidentRepository.findByMonitorIdOrderByStartedAtDesc(10L))
                .thenReturn(List.of(activeIncident, resolvedIncident));

        List<Incident> incidents = incidentRepository.findByMonitorIdOrderByStartedAtDesc(10L);

        assertEquals(2, incidents.size());
        assertEquals(101L, incidents.get(0).getId());
        assertEquals(100L, incidents.get(1).getId());
        verify(incidentRepository).findByMonitorIdOrderByStartedAtDesc(10L);
    }

    @Test
    void findByMonitorUserIdOrderByStartedAtDesc_returnsUserIncidents() {
        when(incidentRepository.findByMonitorUserIdOrderByStartedAtDesc(1L))
                .thenReturn(List.of(activeIncident, resolvedIncident));

        List<Incident> incidents = incidentRepository.findByMonitorUserIdOrderByStartedAtDesc(1L);

        assertEquals(2, incidents.size());
        verify(incidentRepository).findByMonitorUserIdOrderByStartedAtDesc(1L);
    }

    @Test
    void findByMonitorIdAndStatusInOrderByStartedAtDesc_filtersByStatusCollection() {
        when(incidentRepository.findByMonitorIdAndStatusInOrderByStartedAtDesc(10L, List.of("ONGOING", "ACKNOWLEDGED")))
                .thenReturn(List.of(activeIncident));

        List<Incident> incidents = incidentRepository.findByMonitorIdAndStatusInOrderByStartedAtDesc(
                10L, List.of("ONGOING", "ACKNOWLEDGED")
        );

        assertEquals(1, incidents.size());
        assertEquals("ONGOING", incidents.get(0).getStatus());
        verify(incidentRepository).findByMonitorIdAndStatusInOrderByStartedAtDesc(
                10L, List.of("ONGOING", "ACKNOWLEDGED")
        );
    }

    @Test
    void findFirstByMonitorIdAndStatusInOrderByStartedAtDesc_findsTopActiveIncident() {
        when(incidentRepository.findFirstByMonitorIdAndStatusInOrderByStartedAtDesc(10L, List.of("ONGOING", "ACKNOWLEDGED")))
                .thenReturn(Optional.of(activeIncident));

        Optional<Incident> incident = incidentRepository.findFirstByMonitorIdAndStatusInOrderByStartedAtDesc(
                10L, List.of("ONGOING", "ACKNOWLEDGED")
        );

        assertTrue(incident.isPresent());
        assertEquals("ONGOING", incident.get().getStatus());
        verify(incidentRepository).findFirstByMonitorIdAndStatusInOrderByStartedAtDesc(
                10L, List.of("ONGOING", "ACKNOWLEDGED")
        );
    }

    @Test
    void findByMonitorUserIdAndStatusOrderByStartedAtDesc_filtersByUserAndStatus() {
        when(incidentRepository.findByMonitorUserIdAndStatusOrderByStartedAtDesc(1L, "ONGOING"))
                .thenReturn(List.of(activeIncident));

        List<Incident> incidents = incidentRepository.findByMonitorUserIdAndStatusOrderByStartedAtDesc(1L, "ONGOING");

        assertEquals(1, incidents.size());
        assertEquals("ONGOING", incidents.get(0).getStatus());
        verify(incidentRepository).findByMonitorUserIdAndStatusOrderByStartedAtDesc(1L, "ONGOING");
    }

    @Test
    void findByIdAndMonitorUserId_whenOwned_returnsIncident() {
        when(incidentRepository.findByIdAndMonitorUserId(101L, 1L))
                .thenReturn(Optional.of(activeIncident));

        Optional<Incident> result = incidentRepository.findByIdAndMonitorUserId(101L, 1L);

        assertTrue(result.isPresent());
        assertEquals(101L, result.get().getId());
        verify(incidentRepository).findByIdAndMonitorUserId(101L, 1L);
    }

    @Test
    void findByIdAndMonitorUserId_whenNotOwned_returnsEmpty() {
        when(incidentRepository.findByIdAndMonitorUserId(101L, 2L))
                .thenReturn(Optional.empty());

        Optional<Incident> result = incidentRepository.findByIdAndMonitorUserId(101L, 2L);

        assertFalse(result.isPresent());
        verify(incidentRepository).findByIdAndMonitorUserId(101L, 2L);
    }
}

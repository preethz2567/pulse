package dev.preethi.pulse.service;

import dev.preethi.pulse.dto.CreateMonitorRequest;
import dev.preethi.pulse.dto.MonitorResponse;
import dev.preethi.pulse.dto.UpdateMonitorRequest;
import dev.preethi.pulse.entity.Monitor;
import dev.preethi.pulse.entity.User;
import dev.preethi.pulse.exception.MonitorNotFoundException;
import dev.preethi.pulse.repository.MonitorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitorServiceTest {

    @Mock
    private MonitorRepository monitorRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private MonitorService monitorService;

    private User user1;
    private User user2;
    private Monitor monitor1;

    @BeforeEach
    void setUp() {
        user1 = new User("alice@example.com", "hash1", "ROLE_USER");
        user1.setId(1L);

        user2 = new User("bob@example.com", "hash2", "ROLE_USER");
        user2.setId(2L);

        monitor1 = new Monitor(user1, "API Health", "https://api.example.com/health");
        monitor1.setId(100L);
        monitor1.setHttpMethod("GET");
        monitor1.setIntervalSeconds(60);
        monitor1.setTimeoutSeconds(30);
        monitor1.setExpectedStatusCode(200);
        monitor1.setStatus("UP");
        monitor1.setIsActive(true);
    }

    @Test
    void createMonitor_withValidRequest_savesAndReturnsResponse() {
        CreateMonitorRequest request = new CreateMonitorRequest(
                "My App",
                "https://myapp.com",
                "POST",
                30,
                15,
                201,
                true
        );

        when(userService.getUserByEmail("alice@example.com")).thenReturn(user1);

        Monitor savedMonitor = new Monitor(user1, "My App", "https://myapp.com");
        savedMonitor.setId(101L);
        savedMonitor.setHttpMethod("POST");
        savedMonitor.setIntervalSeconds(30);
        savedMonitor.setTimeoutSeconds(15);
        savedMonitor.setExpectedStatusCode(201);
        savedMonitor.setStatus("PENDING");
        savedMonitor.setIsActive(true);

        when(monitorRepository.save(any(Monitor.class))).thenReturn(savedMonitor);

        MonitorResponse response = monitorService.createMonitor("alice@example.com", request);

        assertNotNull(response);
        assertEquals(101L, response.id());
        assertEquals(1L, response.userId());
        assertEquals("My App", response.name());
        assertEquals("https://myapp.com", response.url());
        assertEquals("POST", response.httpMethod());
        assertEquals(30, response.intervalSeconds());
        assertEquals(15, response.timeoutSeconds());
        assertEquals(201, response.expectedStatusCode());
        assertEquals("PENDING", response.status());
        assertTrue(response.isActive());

        ArgumentCaptor<Monitor> captor = ArgumentCaptor.forClass(Monitor.class);
        verify(monitorRepository).save(captor.capture());
        Monitor captured = captor.getValue();
        assertEquals(user1, captured.getUser());
        assertEquals("My App", captured.getName());
    }

    @Test
    void getUserMonitors_returnsOnlyCurrentUserMonitors() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user1);
        when(monitorRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(monitor1));

        List<MonitorResponse> result = monitorService.getUserMonitors("alice@example.com");

        assertEquals(1, result.size());
        assertEquals(100L, result.getFirst().id());
        assertEquals("API Health", result.getFirst().name());
        verify(monitorRepository).findByUserIdOrderByCreatedAtDesc(1L);
    }

    @Test
    void getMonitor_withOwnedMonitor_returnsResponse() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user1);
        when(monitorRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(monitor1));

        MonitorResponse response = monitorService.getMonitor(100L, "alice@example.com");

        assertNotNull(response);
        assertEquals(100L, response.id());
        assertEquals("API Health", response.name());
        verify(monitorRepository).findByIdAndUserId(100L, 1L);
    }

    @Test
    void getMonitor_withUnownedMonitor_throwsMonitorNotFoundException() {
        // user2 tries to access monitor 100 which belongs to user1
        when(userService.getUserByEmail("bob@example.com")).thenReturn(user2);
        when(monitorRepository.findByIdAndUserId(100L, 2L)).thenReturn(Optional.empty());

        MonitorNotFoundException ex = assertThrows(
                MonitorNotFoundException.class,
                () -> monitorService.getMonitor(100L, "bob@example.com")
        );

        assertEquals("Monitor not found with id: 100", ex.getMessage());
        verify(monitorRepository).findByIdAndUserId(100L, 2L);
    }

    @Test
    void updateMonitor_withOwnedMonitor_updatesAndReturnsResponse() {
        UpdateMonitorRequest request = new UpdateMonitorRequest(
                "Updated API Health",
                "https://api.example.com/v2/health",
                "GET",
                120,
                45,
                200,
                false
        );

        when(userService.getUserByEmail("alice@example.com")).thenReturn(user1);
        when(monitorRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(monitor1));
        when(monitorRepository.save(any(Monitor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MonitorResponse response = monitorService.updateMonitor(100L, "alice@example.com", request);

        assertNotNull(response);
        assertEquals("Updated API Health", response.name());
        assertEquals("https://api.example.com/v2/health", response.url());
        assertEquals(120, response.intervalSeconds());
        assertEquals(45, response.timeoutSeconds());
        assertFalse(response.isActive());
        assertEquals("PAUSED", response.status());

        verify(monitorRepository).save(monitor1);
    }

    @Test
    void updateMonitor_withUnownedMonitor_throwsMonitorNotFoundException() {
        UpdateMonitorRequest request = new UpdateMonitorRequest(
                "Attempted Update",
                "https://api.example.com/health",
                "GET",
                60,
                30,
                200,
                true
        );

        when(userService.getUserByEmail("bob@example.com")).thenReturn(user2);
        when(monitorRepository.findByIdAndUserId(100L, 2L)).thenReturn(Optional.empty());

        MonitorNotFoundException ex = assertThrows(
                MonitorNotFoundException.class,
                () -> monitorService.updateMonitor(100L, "bob@example.com", request)
        );

        assertEquals("Monitor not found with id: 100", ex.getMessage());
        verify(monitorRepository, never()).save(any());
    }

    @Test
    void deleteMonitor_withOwnedMonitor_deletesSuccessfully() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user1);
        when(monitorRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(monitor1));

        monitorService.deleteMonitor(100L, "alice@example.com");

        verify(monitorRepository).delete(monitor1);
    }

    @Test
    void deleteMonitor_withUnownedMonitor_throwsMonitorNotFoundException() {
        when(userService.getUserByEmail("bob@example.com")).thenReturn(user2);
        when(monitorRepository.findByIdAndUserId(100L, 2L)).thenReturn(Optional.empty());

        MonitorNotFoundException ex = assertThrows(
                MonitorNotFoundException.class,
                () -> monitorService.deleteMonitor(100L, "bob@example.com")
        );

        assertEquals("Monitor not found with id: 100", ex.getMessage());
        verify(monitorRepository, never()).delete(any());
    }
}

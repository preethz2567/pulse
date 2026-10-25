package dev.preethi.pulse.service;

import dev.preethi.pulse.entity.Monitor;
import dev.preethi.pulse.entity.User;
import dev.preethi.pulse.repository.MonitorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthCheckSchedulerTest {

    @Mock
    private MonitorRepository monitorRepository;

    @Mock
    private MonitorHealthCheckService healthCheckService;

    @InjectMocks
    private HealthCheckScheduler scheduler;

    private User user;
    private Monitor dueMonitor;
    private Monitor notDueMonitor;
    private Monitor inactiveMonitor;

    @BeforeEach
    void setUp() {
        user = new User("alice@example.com", "hash", "ROLE_USER");
        user.setId(1L);

        dueMonitor = new Monitor(user, "Due App", "https://due.example.com");
        dueMonitor.setId(101L);
        dueMonitor.setIntervalSeconds(60);
        dueMonitor.setIsActive(true);
        dueMonitor.setLastCheckedAt(Instant.now().minusSeconds(120)); // checked 2 min ago

        notDueMonitor = new Monitor(user, "Not Due App", "https://notdue.example.com");
        notDueMonitor.setId(102L);
        notDueMonitor.setIntervalSeconds(60);
        notDueMonitor.setIsActive(true);
        notDueMonitor.setLastCheckedAt(Instant.now().minusSeconds(10)); // checked 10s ago

        inactiveMonitor = new Monitor(user, "Inactive App", "https://inactive.example.com");
        inactiveMonitor.setId(103L);
        inactiveMonitor.setIntervalSeconds(60);
        inactiveMonitor.setIsActive(false);
        inactiveMonitor.setLastCheckedAt(Instant.now().minusSeconds(300));
    }

    @Test
    void isDue_whenLastCheckedAtIsNull_returnsTrue() {
        Monitor monitor = new Monitor(user, "New App", "https://new.example.com");
        monitor.setIsActive(true);
        monitor.setLastCheckedAt(null);

        assertTrue(scheduler.isDue(monitor, Instant.now()));
    }

    @Test
    void isDue_whenIntervalHasPassed_returnsTrue() {
        assertTrue(scheduler.isDue(dueMonitor, Instant.now()));
    }

    @Test
    void isDue_whenIntervalHasNotPassed_returnsFalse() {
        assertFalse(scheduler.isDue(notDueMonitor, Instant.now()));
    }

    @Test
    void isDue_whenInactive_returnsFalse() {
        assertFalse(scheduler.isDue(inactiveMonitor, Instant.now()));
    }

    @Test
    void runHealthChecks_checksOnlyDueMonitors() {
        when(monitorRepository.findByIsActiveTrue()).thenReturn(List.of(dueMonitor, notDueMonitor));

        scheduler.runHealthChecks();

        verify(healthCheckService).checkMonitor(101L);
        verify(healthCheckService, never()).checkMonitor(102L);
        verify(healthCheckService, never()).checkMonitor(103L);
    }

    @Test
    void runHealthChecks_whenNoMonitorsDue_doesNotPerformAnyCheck() {
        when(monitorRepository.findByIsActiveTrue()).thenReturn(List.of(notDueMonitor));

        scheduler.runHealthChecks();

        verify(healthCheckService, never()).checkMonitor(any());
    }
}

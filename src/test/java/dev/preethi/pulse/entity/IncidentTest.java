package dev.preethi.pulse.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentTest {

    private User user;
    private Monitor monitor;

    @BeforeEach
    void setUp() {
        user = new User("alice@example.com", "hash", "ROLE_USER");
        user.setId(1L);

        monitor = new Monitor(user, "API Monitor", "https://api.example.com/health");
        monitor.setId(10L);
    }

    @Test
    void defaultConstructor_andPrePersist_initializesDefaults() {
        Incident incident = new Incident();
        incident.setMonitor(monitor);
        incident.setCause("Connection timed out");

        assertNull(incident.getStartedAt());
        assertNull(incident.getCreatedAt());
        assertNull(incident.getUpdatedAt());

        incident.onCreate();

        assertNotNull(incident.getStartedAt());
        assertNotNull(incident.getCreatedAt());
        assertNotNull(incident.getUpdatedAt());
        assertEquals("ONGOING", incident.getStatus());
        assertTrue(incident.isOpen());
        assertFalse(incident.isResolved());
    }

    @Test
    void parameterizedConstructor_setsMonitorCauseAndDefaultStatus() {
        Incident incident = new Incident(monitor, "HTTP 500 Internal Server Error");

        assertEquals(monitor, incident.getMonitor());
        assertEquals("HTTP 500 Internal Server Error", incident.getCause());
        assertEquals("ONGOING", incident.getStatus());
        assertNotNull(incident.getStartedAt());
        assertTrue(incident.isOpen());
        assertFalse(incident.isResolved());
    }

    @Test
    void fullConstructor_setsExplicitFields() {
        Instant started = Instant.parse("2026-10-25T10:00:00Z");
        Incident incident = new Incident(monitor, "ACKNOWLEDGED", "High latency", started);

        assertEquals(monitor, incident.getMonitor());
        assertEquals("ACKNOWLEDGED", incident.getStatus());
        assertEquals("High latency", incident.getCause());
        assertEquals(started, incident.getStartedAt());
        assertTrue(incident.isOpen());
        assertFalse(incident.isResolved());
    }

    @Test
    void isOpen_andIsResolved_lifecycleTransitions() {
        Incident incident = new Incident(monitor, "DNS resolution failure");
        assertEquals("ONGOING", incident.getStatus());
        assertTrue(incident.isOpen());
        assertFalse(incident.isResolved());

        // Acknowledge the incident
        Instant ackTime = Instant.now();
        incident.acknowledge(ackTime);
        assertEquals("ACKNOWLEDGED", incident.getStatus());
        assertEquals(ackTime, incident.getAcknowledgedAt());
        assertTrue(incident.isOpen());
        assertFalse(incident.isResolved());

        // Resolve the incident
        Instant resolveTime = Instant.now();
        incident.resolve(resolveTime);
        assertEquals("RESOLVED", incident.getStatus());
        assertEquals(resolveTime, incident.getResolvedAt());
        assertFalse(incident.isOpen());
        assertTrue(incident.isResolved());
    }

    @Test
    void onUpdate_refreshesUpdatedAtTimestamp() throws InterruptedException {
        Incident incident = new Incident(monitor, "502 Bad Gateway");
        incident.onCreate();

        Instant initialUpdatedAt = incident.getUpdatedAt();
        assertNotNull(initialUpdatedAt);

        Thread.sleep(10);
        incident.onUpdate();

        assertTrue(incident.getUpdatedAt().isAfter(initialUpdatedAt));
    }

    @Test
    void equalsAndHashCode_contract() {
        Incident incident1 = new Incident(monitor, "Error 1");
        incident1.setId(100L);

        Incident incident2 = new Incident(monitor, "Error 2");
        incident2.setId(100L);

        Incident incident3 = new Incident(monitor, "Error 3");
        incident3.setId(200L);

        assertEquals(incident1, incident2);
        assertEquals(incident1.hashCode(), incident2.hashCode());
        assertNotEquals(incident1, incident3);
        assertNotEquals(incident1, null);
        assertNotEquals(incident1, new Object());
    }

    @Test
    void toString_containsEssentialFields() {
        Incident incident = new Incident(monitor, "Connection timeout");
        incident.setId(5L);

        String str = incident.toString();
        assertTrue(str.contains("id=5"));
        assertTrue(str.contains("monitorId=10"));
        assertTrue(str.contains("status='ONGOING'"));
        assertTrue(str.contains("cause='Connection timeout'"));
    }
}

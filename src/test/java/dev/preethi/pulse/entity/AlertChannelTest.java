package dev.preethi.pulse.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertChannelTest {

    @Test
    void constructorAndGetters_workAsExpected() {
        User user = new User("alice@example.com", "hash", "ROLE_USER");
        user.setId(1L);

        Monitor monitor = new Monitor(user, "Test API", "https://api.example.com");
        monitor.setId(10L);

        AlertChannel channel = new AlertChannel(user, monitor, "DevOps Email", "EMAIL", "ops@example.com");
        channel.setId(100L);

        assertEquals(100L, channel.getId());
        assertEquals(user, channel.getUser());
        assertEquals(monitor, channel.getMonitor());
        assertEquals("DevOps Email", channel.getName());
        assertEquals("EMAIL", channel.getType());
        assertEquals("ops@example.com", channel.getDestination());
        assertTrue(channel.getIsEnabled());
    }

    @Test
    void prePersist_setsDefaultsWhenFieldsAreNull() {
        User user = new User("alice@example.com", "hash", "ROLE_USER");
        Monitor monitor = new Monitor(user, "Test API", "https://api.example.com");

        AlertChannel channel = new AlertChannel();
        channel.setUser(user);
        channel.setMonitor(monitor);
        channel.setName("Webhook Channel");
        channel.setDestination("https://hooks.slack.com/services/xxx");

        channel.onCreate();

        assertNotNull(channel.getCreatedAt());
        assertNotNull(channel.getUpdatedAt());
        assertTrue(channel.getIsEnabled());
        assertEquals("EMAIL", channel.getType());
    }

    @Test
    void prePersist_uppercasesType() {
        User user = new User("alice@example.com", "hash", "ROLE_USER");
        Monitor monitor = new Monitor(user, "Test API", "https://api.example.com");

        AlertChannel channel = new AlertChannel(user, monitor, "Webhook Channel", "webhook", "https://example.com/webhook");
        channel.onCreate();

        assertEquals("WEBHOOK", channel.getType());
    }

    @Test
    void preUpdate_updatesTimestampAndNormalizesType() {
        AlertChannel channel = new AlertChannel();
        channel.setType("email");
        channel.onUpdate();

        assertNotNull(channel.getUpdatedAt());
        assertEquals("EMAIL", channel.getType());
    }

    @Test
    void equalsAndHashCode_basedOnId() {
        AlertChannel c1 = new AlertChannel();
        c1.setId(1L);

        AlertChannel c2 = new AlertChannel();
        c2.setId(1L);

        AlertChannel c3 = new AlertChannel();
        c3.setId(2L);

        assertEquals(c1, c2);
        assertNotEquals(c1, c3);
        assertEquals(c1.hashCode(), c2.hashCode());
    }

    @Test
    void toString_containsEssentialFields() {
        AlertChannel channel = new AlertChannel();
        channel.setId(42L);
        channel.setName("AlertOps");
        channel.setType("EMAIL");
        channel.setDestination("alert@example.com");

        String str = channel.toString();
        assertTrue(str.contains("42"));
        assertTrue(str.contains("AlertOps"));
        assertTrue(str.contains("EMAIL"));
        assertTrue(str.contains("alert@example.com"));
    }
}

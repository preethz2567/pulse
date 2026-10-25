package dev.preethi.pulse.repository;

import dev.preethi.pulse.entity.AlertChannel;
import dev.preethi.pulse.entity.Monitor;
import dev.preethi.pulse.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertChannelRepositoryTest {

    @Mock
    private AlertChannelRepository alertChannelRepository;

    private User user;
    private Monitor monitor;
    private AlertChannel emailChannel;
    private AlertChannel webhookChannel;

    @BeforeEach
    void setUp() {
        user = new User("alice@example.com", "hash", "ROLE_USER");
        user.setId(1L);

        monitor = new Monitor(user, "Auth Service", "https://auth.example.com");
        monitor.setId(10L);

        emailChannel = new AlertChannel(user, monitor, "Email Alerts", "EMAIL", "alice@example.com");
        emailChannel.setId(100L);

        webhookChannel = new AlertChannel(user, monitor, "Slack Webhook", "WEBHOOK", "https://hooks.slack.com/services/test");
        webhookChannel.setId(101L);
    }

    @Test
    void findByUserId_returnsUserChannels() {
        when(alertChannelRepository.findByUserId(1L)).thenReturn(List.of(emailChannel, webhookChannel));

        List<AlertChannel> result = alertChannelRepository.findByUserId(1L);

        assertEquals(2, result.size());
        verify(alertChannelRepository).findByUserId(1L);
    }

    @Test
    void findByIdAndUserId_whenMatches_returnsChannel() {
        when(alertChannelRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(emailChannel));

        Optional<AlertChannel> result = alertChannelRepository.findByIdAndUserId(100L, 1L);

        assertTrue(result.isPresent());
        assertEquals("Email Alerts", result.get().getName());
        verify(alertChannelRepository).findByIdAndUserId(100L, 1L);
    }

    @Test
    void findByIdAndUserId_whenNotOwned_returnsEmpty() {
        when(alertChannelRepository.findByIdAndUserId(100L, 2L)).thenReturn(Optional.empty());

        Optional<AlertChannel> result = alertChannelRepository.findByIdAndUserId(100L, 2L);

        assertFalse(result.isPresent());
        verify(alertChannelRepository).findByIdAndUserId(100L, 2L);
    }

    @Test
    void findByMonitorIdAndIsEnabledTrue_returnsEnabledChannels() {
        when(alertChannelRepository.findByMonitorIdAndIsEnabledTrue(10L)).thenReturn(List.of(emailChannel));

        List<AlertChannel> result = alertChannelRepository.findByMonitorIdAndIsEnabledTrue(10L);

        assertEquals(1, result.size());
        assertEquals("EMAIL", result.get(0).getType());
        verify(alertChannelRepository).findByMonitorIdAndIsEnabledTrue(10L);
    }

    @Test
    void findByMonitorIdAndUserIdOrderByCreatedAtDesc_returnsMonitorChannels() {
        when(alertChannelRepository.findByMonitorIdAndUserIdOrderByCreatedAtDesc(10L, 1L))
                .thenReturn(List.of(webhookChannel, emailChannel));

        List<AlertChannel> result = alertChannelRepository.findByMonitorIdAndUserIdOrderByCreatedAtDesc(10L, 1L);

        assertEquals(2, result.size());
        verify(alertChannelRepository).findByMonitorIdAndUserIdOrderByCreatedAtDesc(10L, 1L);
    }

    @Test
    void findByMonitorIdAndTypeIgnoreCaseAndIsEnabledTrue_returnsMatchingChannels() {
        when(alertChannelRepository.findByMonitorIdAndTypeIgnoreCaseAndIsEnabledTrue(10L, "EMAIL"))
                .thenReturn(List.of(emailChannel));

        List<AlertChannel> result = alertChannelRepository.findByMonitorIdAndTypeIgnoreCaseAndIsEnabledTrue(10L, "EMAIL");

        assertEquals(1, result.size());
        assertEquals("EMAIL", result.get(0).getType());
        verify(alertChannelRepository).findByMonitorIdAndTypeIgnoreCaseAndIsEnabledTrue(10L, "EMAIL");
    }
}

package dev.preethi.pulse.service;

import dev.preethi.pulse.dto.AlertChannelResponse;
import dev.preethi.pulse.dto.CreateAlertChannelRequest;
import dev.preethi.pulse.dto.UpdateAlertChannelRequest;
import dev.preethi.pulse.entity.AlertChannel;
import dev.preethi.pulse.entity.Monitor;
import dev.preethi.pulse.entity.User;
import dev.preethi.pulse.exception.AlertChannelNotFoundException;
import dev.preethi.pulse.exception.MonitorNotFoundException;
import dev.preethi.pulse.repository.AlertChannelRepository;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertChannelServiceTest {

    @Mock
    private AlertChannelRepository alertChannelRepository;

    @Mock
    private MonitorRepository monitorRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private AlertChannelService alertChannelService;

    private User user;
    private Monitor monitor;
    private AlertChannel channel;

    @BeforeEach
    void setUp() {
        user = new User("alice@example.com", "hash", "ROLE_USER");
        user.setId(1L);

        monitor = new Monitor(user, "Test Service", "https://api.example.com");
        monitor.setId(10L);

        channel = new AlertChannel(user, monitor, "Email Channel", "EMAIL", "alice@example.com");
        channel.setId(100L);
    }

    @Test
    void createAlertChannel_whenValidAndMonitorOwned_createsSuccessfully() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user);
        when(monitorRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(monitor));
        when(alertChannelRepository.save(any(AlertChannel.class))).thenAnswer(i -> {
            AlertChannel c = i.getArgument(0);
            c.setId(100L);
            return c;
        });

        CreateAlertChannelRequest request = new CreateAlertChannelRequest(
                10L,
                "Slack Webhook",
                "WEBHOOK",
                "https://hooks.slack.com/services/123",
                true
        );

        AlertChannelResponse response = alertChannelService.createAlertChannel("alice@example.com", request);

        assertNotNull(response);
        assertEquals(100L, response.id());
        assertEquals(1L, response.userId());
        assertEquals(10L, response.monitorId());
        assertEquals("Slack Webhook", response.name());
        assertEquals("WEBHOOK", response.type());
        assertEquals("https://hooks.slack.com/services/123", response.destination());
        assertTrue(response.isEnabled());

        ArgumentCaptor<AlertChannel> captor = ArgumentCaptor.forClass(AlertChannel.class);
        verify(alertChannelRepository).save(captor.capture());
        assertEquals("WEBHOOK", captor.getValue().getType());
    }

    @Test
    void createAlertChannel_whenMonitorNotOwnedOrNotFound_throwsMonitorNotFoundException() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user);
        when(monitorRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        CreateAlertChannelRequest request = new CreateAlertChannelRequest(
                99L,
                "Email Channel",
                "EMAIL",
                "alice@example.com",
                true
        );

        assertThrows(MonitorNotFoundException.class, () ->
                alertChannelService.createAlertChannel("alice@example.com", request)
        );

        verify(alertChannelRepository, never()).save(any());
    }

    @Test
    void getAlertChannels_withMonitorId_returnsFilteredChannels() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user);
        when(monitorRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(monitor));
        when(alertChannelRepository.findByMonitorIdAndUserIdOrderByCreatedAtDesc(10L, 1L))
                .thenReturn(List.of(channel));

        List<AlertChannelResponse> result = alertChannelService.getAlertChannels("alice@example.com", 10L);

        assertEquals(1, result.size());
        assertEquals(100L, result.get(0).id());
        assertEquals("Email Channel", result.get(0).name());
    }

    @Test
    void getAlertChannels_withMonitorIdNotOwned_throwsMonitorNotFoundException() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user);
        when(monitorRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(MonitorNotFoundException.class, () ->
                alertChannelService.getAlertChannels("alice@example.com", 99L)
        );

        verify(alertChannelRepository, never()).findByMonitorIdAndUserIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void getAlertChannels_withoutMonitorId_returnsAllUserChannels() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user);
        when(alertChannelRepository.findByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(channel));

        List<AlertChannelResponse> result = alertChannelService.getAlertChannels("alice@example.com", null);

        assertEquals(1, result.size());
        assertEquals(100L, result.get(0).id());
    }

    @Test
    void getAlertChannel_whenOwned_returnsChannelResponse() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user);
        when(alertChannelRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(channel));

        AlertChannelResponse response = alertChannelService.getAlertChannel(100L, "alice@example.com");

        assertNotNull(response);
        assertEquals(100L, response.id());
        assertEquals("Email Channel", response.name());
    }

    @Test
    void getAlertChannel_whenNotOwnedOrNotFound_throwsAlertChannelNotFoundException() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user);
        when(alertChannelRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.empty());

        assertThrows(AlertChannelNotFoundException.class, () ->
                alertChannelService.getAlertChannel(100L, "alice@example.com")
        );
    }

    @Test
    void updateAlertChannel_whenOwned_updatesFields() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user);
        when(alertChannelRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(channel));
        when(alertChannelRepository.save(any(AlertChannel.class))).thenAnswer(i -> i.getArgument(0));

        UpdateAlertChannelRequest request = new UpdateAlertChannelRequest(
                "Updated Name",
                "WEBHOOK",
                "https://webhook.site/abc",
                false
        );

        AlertChannelResponse response = alertChannelService.updateAlertChannel(100L, "alice@example.com", request);

        assertEquals("Updated Name", response.name());
        assertEquals("WEBHOOK", response.type());
        assertEquals("https://webhook.site/abc", response.destination());
        assertEquals(false, response.isEnabled());
        verify(alertChannelRepository).save(channel);
    }

    @Test
    void updateAlertChannel_whenNotOwned_throwsAlertChannelNotFoundException() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user);
        when(alertChannelRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.empty());

        UpdateAlertChannelRequest request = new UpdateAlertChannelRequest(
                "Updated",
                "EMAIL",
                "test@example.com",
                true
        );

        assertThrows(AlertChannelNotFoundException.class, () ->
                alertChannelService.updateAlertChannel(100L, "alice@example.com", request)
        );

        verify(alertChannelRepository, never()).save(any());
    }

    @Test
    void deleteAlertChannel_whenOwned_deletesChannel() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user);
        when(alertChannelRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(channel));

        alertChannelService.deleteAlertChannel(100L, "alice@example.com");

        verify(alertChannelRepository).delete(channel);
    }

    @Test
    void deleteAlertChannel_whenNotOwned_throwsAlertChannelNotFoundException() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user);
        when(alertChannelRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.empty());

        assertThrows(AlertChannelNotFoundException.class, () ->
                alertChannelService.deleteAlertChannel(100L, "alice@example.com")
        );

        verify(alertChannelRepository, never()).delete(any());
    }
}

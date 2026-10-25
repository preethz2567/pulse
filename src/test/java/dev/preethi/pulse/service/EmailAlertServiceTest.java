package dev.preethi.pulse.service;

import dev.preethi.pulse.entity.AlertChannel;
import dev.preethi.pulse.entity.Incident;
import dev.preethi.pulse.entity.Monitor;
import dev.preethi.pulse.entity.User;
import dev.preethi.pulse.repository.AlertChannelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailAlertServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private AlertChannelRepository alertChannelRepository;

    private EmailAlertService emailAlertService;

    private User user;
    private Monitor monitor;
    private Incident openIncident;
    private Incident resolvedIncident;
    private AlertChannel emailChannel1;
    private AlertChannel emailChannel2;

    @BeforeEach
    void setUp() {
        emailAlertService = new EmailAlertService(mailSender, alertChannelRepository, "noreply@pulse.dev");

        user = new User("alice@example.com", "hash", "ROLE_USER");
        user.setId(1L);

        monitor = new Monitor(user, "Payment Gateway", "https://pay.example.com/health");
        monitor.setId(10L);

        openIncident = new Incident(monitor, Incident.STATUS_ONGOING, "HTTP 500 Internal Server Error", Instant.now().minusSeconds(60));
        openIncident.setId(101L);

        resolvedIncident = new Incident(monitor, Incident.STATUS_RESOLVED, "HTTP 500 Internal Server Error", Instant.now().minusSeconds(120));
        resolvedIncident.setId(101L);
        resolvedIncident.setResolvedAt(Instant.now());

        emailChannel1 = new AlertChannel(user, monitor, "DevOps Email", "EMAIL", "ops@example.com");
        emailChannel1.setId(201L);

        emailChannel2 = new AlertChannel(user, monitor, "Lead Email", "EMAIL", "lead@example.com");
        emailChannel2.setId(202L);
    }

    @Test
    void sendIncidentOpenedAlert_whenEnabledEmailChannelsExist_sendsEmailsWithUsefulContext() {
        when(alertChannelRepository.findByMonitorIdAndTypeIgnoreCaseAndIsEnabledTrue(10L, "EMAIL"))
                .thenReturn(List.of(emailChannel1, emailChannel2));

        emailAlertService.sendIncidentOpenedAlert(openIncident);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(2)).send(captor.capture());

        List<SimpleMailMessage> messages = captor.getAllValues();
        assertEquals(2, messages.size());

        SimpleMailMessage first = messages.get(0);
        assertEquals("ops@example.com", first.getTo()[0]);
        assertEquals("noreply@pulse.dev", first.getFrom());
        assertTrue(first.getSubject().contains("[ALERT] Monitor DOWN: Payment Gateway"));
        assertNotNull(first.getText());
        assertTrue(first.getText().contains("Monitor: Payment Gateway"));
        assertTrue(first.getText().contains("URL: https://pay.example.com/health"));
        assertTrue(first.getText().contains("Status: DOWN"));
        assertTrue(first.getText().contains("Event: Incident Opened"));
        assertTrue(first.getText().contains("Cause: HTTP 500 Internal Server Error"));

        SimpleMailMessage second = messages.get(1);
        assertEquals("lead@example.com", second.getTo()[0]);
    }

    @Test
    void sendIncidentOpenedAlert_whenNoChannelsFound_doesNotSendEmail() {
        when(alertChannelRepository.findByMonitorIdAndTypeIgnoreCaseAndIsEnabledTrue(10L, "EMAIL"))
                .thenReturn(List.of());

        emailAlertService.sendIncidentOpenedAlert(openIncident);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendIncidentOpenedAlert_whenMailSenderThrows_catchesAndDoesNotPropagate() {
        when(alertChannelRepository.findByMonitorIdAndTypeIgnoreCaseAndIsEnabledTrue(10L, "EMAIL"))
                .thenReturn(List.of(emailChannel1));
        doThrow(new MailSendException("SMTP connection refused")).when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> emailAlertService.sendIncidentOpenedAlert(openIncident));
    }

    @Test
    void sendIncidentOpenedAlert_whenOneRecipientFails_stillAttemptsOtherRecipients() {
        when(alertChannelRepository.findByMonitorIdAndTypeIgnoreCaseAndIsEnabledTrue(10L, "EMAIL"))
                .thenReturn(List.of(emailChannel1, emailChannel2));

        doThrow(new MailSendException("SMTP timeout on first recipient"))
                .doNothing()
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> emailAlertService.sendIncidentOpenedAlert(openIncident));

        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendIncidentOpenedAlert_whenIncidentOrMonitorNull_doesNotSend() {
        emailAlertService.sendIncidentOpenedAlert(null);
        emailAlertService.sendIncidentOpenedAlert(new Incident());

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendIncidentResolvedAlert_whenEnabledEmailChannelsExist_sendsRecoveryEmail() {
        when(alertChannelRepository.findByMonitorIdAndTypeIgnoreCaseAndIsEnabledTrue(10L, "EMAIL"))
                .thenReturn(List.of(emailChannel1));

        emailAlertService.sendIncidentResolvedAlert(resolvedIncident);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();
        assertEquals("ops@example.com", message.getTo()[0]);
        assertTrue(message.getSubject().contains("[RECOVERY] Monitor UP: Payment Gateway"));
        assertNotNull(message.getText());
        assertTrue(message.getText().contains("Monitor: Payment Gateway"));
        assertTrue(message.getText().contains("URL: https://pay.example.com/health"));
        assertTrue(message.getText().contains("Status: UP"));
        assertTrue(message.getText().contains("Event: Incident Resolved"));
        assertTrue(message.getText().contains("Resolved At:"));
    }

    @Test
    void sendIncidentResolvedAlert_whenMailSenderThrows_catchesAndDoesNotPropagate() {
        when(alertChannelRepository.findByMonitorIdAndTypeIgnoreCaseAndIsEnabledTrue(10L, "EMAIL"))
                .thenReturn(List.of(emailChannel1));
        doThrow(new MailSendException("Mailpit connection error")).when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> emailAlertService.sendIncidentResolvedAlert(resolvedIncident));
    }
}

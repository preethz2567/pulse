package dev.preethi.pulse.service;

import dev.preethi.pulse.entity.AlertChannel;
import dev.preethi.pulse.entity.Incident;
import dev.preethi.pulse.entity.Monitor;
import dev.preethi.pulse.repository.AlertChannelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmailAlertService {

    private static final Logger log = LoggerFactory.getLogger(EmailAlertService.class);

    private final JavaMailSender mailSender;
    private final AlertChannelRepository alertChannelRepository;
    private final String fromAddress;

    public EmailAlertService(
            JavaMailSender mailSender,
            AlertChannelRepository alertChannelRepository,
            @Value("${pulse.mail.from:noreply@pulse.dev}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.alertChannelRepository = alertChannelRepository;
        this.fromAddress = (fromAddress != null && !fromAddress.isBlank()) ? fromAddress : "noreply@pulse.dev";
    }

    public void sendIncidentOpenedAlert(Incident incident) {
        if (incident == null || incident.getMonitor() == null || incident.getMonitor().getId() == null) {
            return;
        }

        Monitor monitor = incident.getMonitor();
        List<AlertChannel> channels = alertChannelRepository.findByMonitorIdAndTypeIgnoreCaseAndIsEnabledTrue(
                monitor.getId(),
                AlertChannel.TYPE_EMAIL
        );

        if (channels.isEmpty()) {
            log.debug("No enabled EMAIL alert channels found for monitor ID {}", monitor.getId());
            return;
        }

        String subject = String.format("[ALERT] Monitor DOWN: %s", monitor.getName());
        String body = String.format("""
                Incident Alert: Monitor is DOWN

                Monitor: %s
                URL: %s
                Status: DOWN
                Event: Incident Opened
                Started At: %s
                Cause: %s

                Pulse Monitoring System
                """,
                monitor.getName(),
                monitor.getUrl(),
                incident.getStartedAt() != null ? incident.getStartedAt().toString() : "N/A",
                incident.getCause() != null ? incident.getCause() : "Health check failed"
        );

        sendToChannels(channels, subject, body);
    }

    public void sendIncidentResolvedAlert(Incident incident) {
        if (incident == null || incident.getMonitor() == null || incident.getMonitor().getId() == null) {
            return;
        }

        Monitor monitor = incident.getMonitor();
        List<AlertChannel> channels = alertChannelRepository.findByMonitorIdAndTypeIgnoreCaseAndIsEnabledTrue(
                monitor.getId(),
                AlertChannel.TYPE_EMAIL
        );

        if (channels.isEmpty()) {
            log.debug("No enabled EMAIL alert channels found for monitor ID {}", monitor.getId());
            return;
        }

        String subject = String.format("[RECOVERY] Monitor UP: %s", monitor.getName());
        String body = String.format("""
                Recovery Alert: Monitor is BACK UP

                Monitor: %s
                URL: %s
                Status: UP
                Event: Incident Resolved
                Started At: %s
                Resolved At: %s

                Pulse Monitoring System
                """,
                monitor.getName(),
                monitor.getUrl(),
                incident.getStartedAt() != null ? incident.getStartedAt().toString() : "N/A",
                incident.getResolvedAt() != null ? incident.getResolvedAt().toString() : "N/A"
        );

        sendToChannels(channels, subject, body);
    }

    private void sendToChannels(List<AlertChannel> channels, String subject, String text) {
        for (AlertChannel channel : channels) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromAddress);
                message.setTo(channel.getDestination());
                message.setSubject(subject);
                message.setText(text);

                mailSender.send(message);
                log.info("Sent email alert to channel ID {} (destination: {})", channel.getId(), channel.getDestination());
            } catch (MailException e) {
                log.error("Failed to deliver email alert to channel ID {}: {}", channel.getId(), e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error delivering email alert to channel ID {}: {}", channel.getId(), e.getMessage());
            }
        }
    }
}

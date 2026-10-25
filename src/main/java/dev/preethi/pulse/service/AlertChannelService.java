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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AlertChannelService {

    private final AlertChannelRepository alertChannelRepository;
    private final MonitorRepository monitorRepository;
    private final UserService userService;

    public AlertChannelService(
            AlertChannelRepository alertChannelRepository,
            MonitorRepository monitorRepository,
            UserService userService
    ) {
        this.alertChannelRepository = alertChannelRepository;
        this.monitorRepository = monitorRepository;
        this.userService = userService;
    }

    @Transactional
    public AlertChannelResponse createAlertChannel(String userEmail, CreateAlertChannelRequest request) {
        User user = userService.getUserByEmail(userEmail);

        Monitor monitor = monitorRepository.findByIdAndUserId(request.monitorId(), user.getId())
                .orElseThrow(() -> new MonitorNotFoundException("Monitor not found with id: " + request.monitorId()));

        AlertChannel channel = new AlertChannel(user, monitor, request.name(), request.type(), request.destination());
        channel.setIsEnabled(request.isEnabled());

        AlertChannel saved = alertChannelRepository.save(channel);
        return AlertChannelResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<AlertChannelResponse> getAlertChannels(String userEmail, Long monitorId) {
        User user = userService.getUserByEmail(userEmail);

        if (monitorId != null) {
            // Validate monitor ownership
            monitorRepository.findByIdAndUserId(monitorId, user.getId())
                    .orElseThrow(() -> new MonitorNotFoundException("Monitor not found with id: " + monitorId));
            return alertChannelRepository.findByMonitorIdAndUserIdOrderByCreatedAtDesc(monitorId, user.getId())
                    .stream()
                    .map(AlertChannelResponse::from)
                    .toList();
        }

        return alertChannelRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(AlertChannelResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AlertChannelResponse getAlertChannel(Long id, String userEmail) {
        User user = userService.getUserByEmail(userEmail);

        AlertChannel channel = alertChannelRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new AlertChannelNotFoundException("Alert channel not found with id: " + id));

        return AlertChannelResponse.from(channel);
    }

    @Transactional
    public AlertChannelResponse updateAlertChannel(Long id, String userEmail, UpdateAlertChannelRequest request) {
        User user = userService.getUserByEmail(userEmail);

        AlertChannel channel = alertChannelRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new AlertChannelNotFoundException("Alert channel not found with id: " + id));

        channel.setName(request.name());
        channel.setType(request.type());
        channel.setDestination(request.destination());
        channel.setIsEnabled(request.isEnabled());

        AlertChannel updated = alertChannelRepository.save(channel);
        return AlertChannelResponse.from(updated);
    }

    @Transactional
    public void deleteAlertChannel(Long id, String userEmail) {
        User user = userService.getUserByEmail(userEmail);

        AlertChannel channel = alertChannelRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new AlertChannelNotFoundException("Alert channel not found with id: " + id));

        alertChannelRepository.delete(channel);
    }
}

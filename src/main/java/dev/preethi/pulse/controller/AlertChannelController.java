package dev.preethi.pulse.controller;

import dev.preethi.pulse.dto.AlertChannelResponse;
import dev.preethi.pulse.dto.CreateAlertChannelRequest;
import dev.preethi.pulse.dto.UpdateAlertChannelRequest;
import dev.preethi.pulse.service.AlertChannelService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/alert-channels")
public class AlertChannelController {

    private final AlertChannelService alertChannelService;

    public AlertChannelController(AlertChannelService alertChannelService) {
        this.alertChannelService = alertChannelService;
    }

    @PostMapping
    public ResponseEntity<AlertChannelResponse> createAlertChannel(
            @Valid @RequestBody CreateAlertChannelRequest request,
            Principal principal
    ) {
        AlertChannelResponse response = alertChannelService.createAlertChannel(principal.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<AlertChannelResponse>> listAlertChannels(
            @RequestParam(required = false) Long monitorId,
            Principal principal
    ) {
        List<AlertChannelResponse> channels = alertChannelService.getAlertChannels(principal.getName(), monitorId);
        return ResponseEntity.ok(channels);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertChannelResponse> getAlertChannel(
            @PathVariable Long id,
            Principal principal
    ) {
        AlertChannelResponse channel = alertChannelService.getAlertChannel(id, principal.getName());
        return ResponseEntity.ok(channel);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AlertChannelResponse> updateAlertChannel(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAlertChannelRequest request,
            Principal principal
    ) {
        AlertChannelResponse updated = alertChannelService.updateAlertChannel(id, principal.getName(), request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAlertChannel(
            @PathVariable Long id,
            Principal principal
    ) {
        alertChannelService.deleteAlertChannel(id, principal.getName());
        return ResponseEntity.noContent().build();
    }
}

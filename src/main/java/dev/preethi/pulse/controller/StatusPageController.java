package dev.preethi.pulse.controller;

import dev.preethi.pulse.dto.CreateStatusPageRequest;
import dev.preethi.pulse.dto.StatusPageResponse;
import dev.preethi.pulse.dto.UpdateStatusPageRequest;
import dev.preethi.pulse.service.StatusPageService;
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
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/status-pages")
public class StatusPageController {

    private final StatusPageService statusPageService;

    public StatusPageController(StatusPageService statusPageService) {
        this.statusPageService = statusPageService;
    }

    @PostMapping
    public ResponseEntity<StatusPageResponse> createStatusPage(
            @Valid @RequestBody CreateStatusPageRequest request,
            Principal principal
    ) {
        StatusPageResponse created = statusPageService.createStatusPage(principal.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<StatusPageResponse>> getUserStatusPages(Principal principal) {
        List<StatusPageResponse> statusPages = statusPageService.getUserStatusPages(principal.getName());
        return ResponseEntity.ok(statusPages);
    }

    @GetMapping("/{id}")
    public ResponseEntity<StatusPageResponse> getStatusPage(
            @PathVariable Long id,
            Principal principal
    ) {
        StatusPageResponse statusPage = statusPageService.getStatusPage(id, principal.getName());
        return ResponseEntity.ok(statusPage);
    }

    @PutMapping("/{id}")
    public ResponseEntity<StatusPageResponse> updateStatusPage(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusPageRequest request,
            Principal principal
    ) {
        StatusPageResponse updated = statusPageService.updateStatusPage(id, principal.getName(), request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStatusPage(
            @PathVariable Long id,
            Principal principal
    ) {
        statusPageService.deleteStatusPage(id, principal.getName());
        return ResponseEntity.noContent().build();
    }
}

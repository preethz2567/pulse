package dev.preethi.pulse.controller;

import dev.preethi.pulse.dto.PublicStatusPageResponse;
import dev.preethi.pulse.service.PublicStatusService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/status")
public class PublicStatusController {

    private final PublicStatusService publicStatusService;

    public PublicStatusController(PublicStatusService publicStatusService) {
        this.publicStatusService = publicStatusService;
    }

    @GetMapping("/{slug}")
    public ResponseEntity<PublicStatusPageResponse> getPublicStatus(@PathVariable String slug) {
        PublicStatusPageResponse response = publicStatusService.getPublicStatus(slug);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic())
                .body(response);
    }
}

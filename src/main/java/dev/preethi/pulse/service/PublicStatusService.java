package dev.preethi.pulse.service;

import dev.preethi.pulse.dto.PublicIncidentResponse;
import dev.preethi.pulse.dto.PublicMonitorStatusResponse;
import dev.preethi.pulse.dto.PublicStatusPageResponse;
import dev.preethi.pulse.entity.Incident;
import dev.preethi.pulse.entity.Monitor;
import dev.preethi.pulse.entity.StatusPage;
import dev.preethi.pulse.exception.StatusPageNotFoundException;
import dev.preethi.pulse.repository.IncidentRepository;
import dev.preethi.pulse.repository.StatusPageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Optional;

@Service
public class PublicStatusService {

    private final StatusPageRepository statusPageRepository;
    private final IncidentRepository incidentRepository;
    private final PublicStatusCache publicStatusCache;
    private final Clock clock;

    @Autowired
    public PublicStatusService(
            StatusPageRepository statusPageRepository,
            IncidentRepository incidentRepository,
            PublicStatusCache publicStatusCache
    ) {
        this(statusPageRepository, incidentRepository, publicStatusCache, Clock.systemUTC());
    }

    public PublicStatusService(
            StatusPageRepository statusPageRepository,
            IncidentRepository incidentRepository,
            PublicStatusCache publicStatusCache,
            Clock clock
    ) {
        this.statusPageRepository = statusPageRepository;
        this.incidentRepository = incidentRepository;
        this.publicStatusCache = publicStatusCache;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Transactional(readOnly = true)
    public PublicStatusPageResponse getPublicStatus(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new StatusPageNotFoundException("Status page not found with slug: " + slug);
        }

        // 1. Check cache first
        Optional<PublicStatusPageResponse> cached = publicStatusCache.get(slug);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 2. Fetch from database with associated monitors
        StatusPage statusPage = statusPageRepository.findBySlugWithMonitors(slug)
                .orElseThrow(() -> new StatusPageNotFoundException("Status page not found with slug: " + slug));

        // 3. Do not reveal whether unpublished page exists
        if (!Boolean.TRUE.equals(statusPage.getIsPublished())) {
            publicStatusCache.evict(slug);
            throw new StatusPageNotFoundException("Status page not found with slug: " + slug);
        }

        // 4. Map monitors safely (no URLs or private configs)
        List<Monitor> monitors = statusPage.getMonitors() != null
                ? new ArrayList<>(statusPage.getMonitors())
                : List.of();

        List<PublicMonitorStatusResponse> monitorResponses = monitors.stream()
                .map(PublicMonitorStatusResponse::from)
                .toList();

        // 5. Calculate overall status based on existing Pulse status conventions
        String overallStatus = calculateOverallStatus(monitors);

        // 6. Fetch relevant active and resolved incidents without N+1 queries
        List<PublicIncidentResponse> incidentResponses = List.of();
        if (!monitors.isEmpty()) {
            List<Long> monitorIds = monitors.stream().map(Monitor::getId).toList();
            Instant recentCutoff = Instant.now(clock).minus(Duration.ofDays(7));
            List<Incident> incidents = incidentRepository.findPublicIncidentsByMonitorIds(monitorIds, recentCutoff);
            incidentResponses = incidents.stream()
                    .map(PublicIncidentResponse::from)
                    .toList();
        }

        // 7. Calculate last updated timestamp
        Instant updatedAt = statusPage.getUpdatedAt() != null ? statusPage.getUpdatedAt() : Instant.now(clock);
        for (Monitor m : monitors) {
            if (m.getLastCheckedAt() != null && m.getLastCheckedAt().isAfter(updatedAt)) {
                updatedAt = m.getLastCheckedAt();
            }
        }

        PublicStatusPageResponse response = new PublicStatusPageResponse(
                statusPage.getName(),
                statusPage.getDescription(),
                overallStatus,
                monitorResponses,
                incidentResponses,
                updatedAt
        );

        // 8. Put in cache with 30s TTL
        publicStatusCache.put(slug, response);

        return response;
    }

    private String calculateOverallStatus(List<Monitor> monitors) {
        if (monitors.isEmpty()) {
            return "UP";
        }

        long upCount = monitors.stream().filter(m -> "UP".equalsIgnoreCase(m.getStatus())).count();
        long downCount = monitors.stream().filter(m -> "DOWN".equalsIgnoreCase(m.getStatus())).count();
        long total = monitors.size();

        if (downCount == 0) {
            if (upCount > 0) {
                return "UP";
            }
            boolean allPaused = monitors.stream().allMatch(m -> "PAUSED".equalsIgnoreCase(m.getStatus()));
            if (allPaused) {
                return "PAUSED";
            }
            return "PENDING";
        }

        if (downCount == total) {
            return "DOWN";
        }

        return "DEGRADED";
    }
}

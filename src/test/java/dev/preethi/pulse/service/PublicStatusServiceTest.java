package dev.preethi.pulse.service;

import dev.preethi.pulse.dto.PublicIncidentResponse;
import dev.preethi.pulse.dto.PublicMonitorStatusResponse;
import dev.preethi.pulse.dto.PublicStatusPageResponse;
import dev.preethi.pulse.entity.Incident;
import dev.preethi.pulse.entity.Monitor;
import dev.preethi.pulse.entity.StatusPage;
import dev.preethi.pulse.entity.User;
import dev.preethi.pulse.exception.StatusPageNotFoundException;
import dev.preethi.pulse.repository.IncidentRepository;
import dev.preethi.pulse.repository.StatusPageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicStatusServiceTest {

    @Mock
    private StatusPageRepository statusPageRepository;

    @Mock
    private IncidentRepository incidentRepository;

    private PublicStatusCache publicStatusCache;
    private PublicStatusService publicStatusService;

    private final Instant fixedNow = Instant.parse("2026-10-31T12:00:00Z");
    private final AtomicReference<Instant> currentInstant = new AtomicReference<>(fixedNow);
    private final Clock mutableClock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentInstant.get();
        }
    };

    private User user;
    private Monitor monitor1;
    private Monitor monitor2;
    private StatusPage publishedPage;
    private StatusPage unpublishedPage;

    @BeforeEach
    void setUp() {
        currentInstant.set(fixedNow);
        publicStatusCache = new PublicStatusCache(mutableClock);
        publicStatusService = new PublicStatusService(statusPageRepository, incidentRepository, publicStatusCache, mutableClock);

        user = new User("admin@example.com", "secretHash", "ROLE_USER");
        user.setId(1L);

        monitor1 = new Monitor(user, "API Service", "https://api.internal.corp/private/health");
        monitor1.setId(10L);
        monitor1.setStatus("UP");
        monitor1.setLastCheckedAt(fixedNow.minusSeconds(60));

        monitor2 = new Monitor(user, "Payment Gateway", "https://payments.internal.corp/secret/endpoint");
        monitor2.setId(20L);
        monitor2.setStatus("UP");
        monitor2.setLastCheckedAt(fixedNow.minusSeconds(30));

        publishedPage = new StatusPage(
                user,
                "Acme Status",
                "acme",
                "Public status of Acme services",
                true,
                new LinkedHashSet<>(List.of(monitor1, monitor2))
        );
        publishedPage.setId(100L);
        publishedPage.setUpdatedAt(fixedNow.minusSeconds(120));

        unpublishedPage = new StatusPage(
                user,
                "Draft Page",
                "draft",
                "Internal staging only",
                false,
                new LinkedHashSet<>(List.of(monitor1))
        );
        unpublishedPage.setId(101L);
    }

    @Test
    void getPublicStatus_whenPublished_returns200AndApprovedFields() {
        when(statusPageRepository.findBySlugWithMonitors("acme")).thenReturn(Optional.of(publishedPage));
        when(incidentRepository.findPublicIncidentsByMonitorIds(eq(List.of(10L, 20L)), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        PublicStatusPageResponse response = publicStatusService.getPublicStatus("acme");

        assertNotNull(response);
        assertEquals("Acme Status", response.name());
        assertEquals("Public status of Acme services", response.description());
        assertEquals("UP", response.overallStatus());
        assertEquals(2, response.monitors().size());
        assertEquals(fixedNow.minusSeconds(30), response.updatedAt());

        // Verify approved monitor fields only
        PublicMonitorStatusResponse m1 = response.monitors().get(0);
        assertEquals(10L, m1.id());
        assertEquals("API Service", m1.name());
        assertEquals("UP", m1.status());
        assertEquals(fixedNow.minusSeconds(60), m1.lastCheckedAt());
    }

    @Test
    void getPublicStatus_whenUnpublished_throwsStatusPageNotFoundException() {
        when(statusPageRepository.findBySlugWithMonitors("draft")).thenReturn(Optional.of(unpublishedPage));

        StatusPageNotFoundException ex = assertThrows(
                StatusPageNotFoundException.class,
                () -> publicStatusService.getPublicStatus("draft")
        );

        assertEquals("Status page not found with slug: draft", ex.getMessage());
        verify(incidentRepository, never()).findPublicIncidentsByMonitorIds(any(), any());
        assertTrue(publicStatusCache.get("draft").isEmpty());
    }

    @Test
    void getPublicStatus_whenNonExistent_throwsStatusPageNotFoundException() {
        when(statusPageRepository.findBySlugWithMonitors("non-existent")).thenReturn(Optional.empty());

        StatusPageNotFoundException ex = assertThrows(
                StatusPageNotFoundException.class,
                () -> publicStatusService.getPublicStatus("non-existent")
        );

        assertEquals("Status page not found with slug: non-existent", ex.getMessage());
    }

    @Test
    void getPublicStatus_emptyMonitors_returnsUpOverallStatus() {
        StatusPage emptyPage = new StatusPage(user, "Empty Page", "empty-page", "No monitors yet", true, Set.of());
        when(statusPageRepository.findBySlugWithMonitors("empty-page")).thenReturn(Optional.of(emptyPage));

        PublicStatusPageResponse response = publicStatusService.getPublicStatus("empty-page");

        assertNotNull(response);
        assertEquals("UP", response.overallStatus());
        assertTrue(response.monitors().isEmpty());
        assertTrue(response.incidents().isEmpty());
        verify(incidentRepository, never()).findPublicIncidentsByMonitorIds(any(), any());
    }

    @Test
    void getPublicStatus_multipleMonitorsDifferentStatuses_returnsDegradedOverallStatus() {
        monitor1.setStatus("UP");
        monitor2.setStatus("DOWN");

        when(statusPageRepository.findBySlugWithMonitors("acme")).thenReturn(Optional.of(publishedPage));
        when(incidentRepository.findPublicIncidentsByMonitorIds(any(), any())).thenReturn(List.of());

        PublicStatusPageResponse response = publicStatusService.getPublicStatus("acme");

        assertNotNull(response);
        assertEquals("DEGRADED", response.overallStatus());
        assertEquals(2, response.monitors().size());
        assertEquals("UP", response.monitors().get(0).status());
        assertEquals("DOWN", response.monitors().get(1).status());
    }

    @Test
    void getPublicStatus_allMonitorsDown_returnsDownOverallStatus() {
        monitor1.setStatus("DOWN");
        monitor2.setStatus("DOWN");

        when(statusPageRepository.findBySlugWithMonitors("acme")).thenReturn(Optional.of(publishedPage));
        when(incidentRepository.findPublicIncidentsByMonitorIds(any(), any())).thenReturn(List.of());

        PublicStatusPageResponse response = publicStatusService.getPublicStatus("acme");

        assertNotNull(response);
        assertEquals("DOWN", response.overallStatus());
    }

    @Test
    void getPublicStatus_allMonitorsPending_returnsPendingOverallStatus() {
        monitor1.setStatus("PENDING");
        monitor2.setStatus("PENDING");

        when(statusPageRepository.findBySlugWithMonitors("acme")).thenReturn(Optional.of(publishedPage));
        when(incidentRepository.findPublicIncidentsByMonitorIds(any(), any())).thenReturn(List.of());

        PublicStatusPageResponse response = publicStatusService.getPublicStatus("acme");

        assertNotNull(response);
        assertEquals("PENDING", response.overallStatus());
    }

    @Test
    void getPublicStatus_mixOfPendingAndPausedMonitors_returnsPendingOverallStatus() {
        monitor1.setStatus("PENDING");
        monitor2.setStatus("PAUSED");

        when(statusPageRepository.findBySlugWithMonitors("acme")).thenReturn(Optional.of(publishedPage));
        when(incidentRepository.findPublicIncidentsByMonitorIds(any(), any())).thenReturn(List.of());

        PublicStatusPageResponse response = publicStatusService.getPublicStatus("acme");

        assertNotNull(response);
        assertEquals("PENDING", response.overallStatus());
    }

    @Test
    void getPublicStatus_allMonitorsPaused_returnsPausedOverallStatus() {
        monitor1.setStatus("PAUSED");
        monitor2.setStatus("PAUSED");

        when(statusPageRepository.findBySlugWithMonitors("acme")).thenReturn(Optional.of(publishedPage));
        when(incidentRepository.findPublicIncidentsByMonitorIds(any(), any())).thenReturn(List.of());

        PublicStatusPageResponse response = publicStatusService.getPublicStatus("acme");

        assertNotNull(response);
        assertEquals("PAUSED", response.overallStatus());
    }

    @Test
    void getPublicStatus_includesActiveAndRecentResolvedIncidents() {
        Incident activeIncident = new Incident(monitor2, "ONGOING", "Gateway 502 Bad Gateway", fixedNow.minusSeconds(600));
        activeIncident.setId(501L);

        Incident resolvedIncident = new Incident(monitor1, "RESOLVED", "High latency", fixedNow.minusSeconds(3600));
        resolvedIncident.setId(500L);
        resolvedIncident.setResolvedAt(fixedNow.minusSeconds(1800));

        when(statusPageRepository.findBySlugWithMonitors("acme")).thenReturn(Optional.of(publishedPage));
        when(incidentRepository.findPublicIncidentsByMonitorIds(eq(List.of(10L, 20L)), any(Instant.class)))
                .thenReturn(List.of(activeIncident, resolvedIncident));

        PublicStatusPageResponse response = publicStatusService.getPublicStatus("acme");

        assertNotNull(response);
        assertEquals(2, response.incidents().size());

        PublicIncidentResponse i1 = response.incidents().get(0);
        assertEquals(501L, i1.id());
        assertEquals(20L, i1.monitorId());
        assertEquals("Payment Gateway", i1.monitorName());
        assertEquals("ONGOING", i1.status());
        assertEquals("Gateway 502 Bad Gateway", i1.cause());
        assertNull(i1.resolvedAt());

        PublicIncidentResponse i2 = response.incidents().get(1);
        assertEquals(500L, i2.id());
        assertEquals("RESOLVED", i2.status());
        assertEquals(fixedNow.minusSeconds(1800), i2.resolvedAt());
    }

    @Test
    void getPublicStatus_sanitizesSsrfCauseMessage() {
        Incident ssrfIncident = new Incident(monitor1, "ONGOING", "SSRF Protection: Access to loopback address is prohibited: 127.0.0.1", fixedNow.minusSeconds(100));
        ssrfIncident.setId(600L);

        when(statusPageRepository.findBySlugWithMonitors("acme")).thenReturn(Optional.of(publishedPage));
        when(incidentRepository.findPublicIncidentsByMonitorIds(any(), any())).thenReturn(List.of(ssrfIncident));

        PublicStatusPageResponse response = publicStatusService.getPublicStatus("acme");

        assertEquals(1, response.incidents().size());
        assertEquals("Connection policy violation", response.incidents().get(0).cause());
        assertFalse(response.incidents().get(0).cause().contains("127.0.0.1"));
    }

    @Test
    void getPublicStatus_sanitizesInternalUrlsAndIpsInIncidentCause() {
        Incident urlIncident = new Incident(monitor1, "ONGOING", "Connection error: Connect to https://192.168.1.100:8443 failed: Connection refused", fixedNow.minusSeconds(100));
        urlIncident.setId(601L);

        when(statusPageRepository.findBySlugWithMonitors("acme")).thenReturn(Optional.of(publishedPage));
        when(incidentRepository.findPublicIncidentsByMonitorIds(any(), any())).thenReturn(List.of(urlIncident));

        PublicStatusPageResponse response = publicStatusService.getPublicStatus("acme");

        assertEquals(1, response.incidents().size());
        assertEquals("Connection error", response.incidents().get(0).cause());
        assertFalse(response.incidents().get(0).cause().contains("https://"));
        assertFalse(response.incidents().get(0).cause().contains("192.168.1.100"));
    }

    @Test
    void getPublicStatus_servesFromCacheWithin30Seconds() {
        when(statusPageRepository.findBySlugWithMonitors("acme")).thenReturn(Optional.of(publishedPage));
        when(incidentRepository.findPublicIncidentsByMonitorIds(any(), any())).thenReturn(List.of());

        // First call populates cache
        PublicStatusPageResponse response1 = publicStatusService.getPublicStatus("acme");
        assertNotNull(response1);

        // Second call should serve from cache (repository not invoked again)
        PublicStatusPageResponse response2 = publicStatusService.getPublicStatus("acme");
        assertEquals(response1, response2);

        verify(statusPageRepository, times(1)).findBySlugWithMonitors("acme");
    }

    @Test
    void getPublicStatus_refetchesFromDatabaseAfter30SecondsExpiry() {
        when(statusPageRepository.findBySlugWithMonitors("acme")).thenReturn(Optional.of(publishedPage));
        when(incidentRepository.findPublicIncidentsByMonitorIds(any(), any())).thenReturn(List.of());

        // Initial call populates cache
        publicStatusService.getPublicStatus("acme");
        verify(statusPageRepository, times(1)).findBySlugWithMonitors("acme");

        // Fast forward clock by 31 seconds
        currentInstant.set(fixedNow.plusSeconds(31));

        // Next call should miss expired cache and query database again
        publicStatusService.getPublicStatus("acme");
        verify(statusPageRepository, times(2)).findBySlugWithMonitors("acme");
    }

    @Test
    void getPublicStatus_evictsCacheIfPageBecomesUnpublished() {
        when(statusPageRepository.findBySlugWithMonitors("acme")).thenReturn(Optional.of(publishedPage));
        when(incidentRepository.findPublicIncidentsByMonitorIds(any(), any())).thenReturn(List.of());

        // Initial call caches response
        publicStatusService.getPublicStatus("acme");
        assertTrue(publicStatusCache.get("acme").isPresent());

        // When cache expires or is evicted and page is now unpublished in DB
        currentInstant.set(fixedNow.plusSeconds(31));
        publishedPage.setIsPublished(false);
        assertThrows(StatusPageNotFoundException.class, () -> publicStatusService.getPublicStatus("acme"));
        assertTrue(publicStatusCache.get("acme").isEmpty());
    }

    @Test
    void publicStatusCache_invalidatesOnEvict() {
        when(statusPageRepository.findBySlugWithMonitors("acme")).thenReturn(Optional.of(publishedPage));
        when(incidentRepository.findPublicIncidentsByMonitorIds(any(), any())).thenReturn(List.of());

        publicStatusService.getPublicStatus("acme");
        assertTrue(publicStatusCache.get("acme").isPresent());

        publicStatusCache.evict("acme");
        assertTrue(publicStatusCache.get("acme").isEmpty());
    }
}

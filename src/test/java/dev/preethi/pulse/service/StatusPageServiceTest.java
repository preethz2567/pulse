package dev.preethi.pulse.service;

import dev.preethi.pulse.dto.CreateStatusPageRequest;
import dev.preethi.pulse.dto.StatusPageResponse;
import dev.preethi.pulse.dto.UpdateStatusPageRequest;
import dev.preethi.pulse.entity.Monitor;
import dev.preethi.pulse.entity.StatusPage;
import dev.preethi.pulse.entity.User;
import dev.preethi.pulse.exception.DuplicateSlugException;
import dev.preethi.pulse.exception.MonitorNotFoundException;
import dev.preethi.pulse.exception.StatusPageNotFoundException;
import dev.preethi.pulse.repository.MonitorRepository;
import dev.preethi.pulse.repository.StatusPageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatusPageServiceTest {

    @Mock
    private StatusPageRepository statusPageRepository;

    @Mock
    private MonitorRepository monitorRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private StatusPageService statusPageService;

    private User user1;
    private User user2;
    private Monitor monitor1;
    private Monitor monitor2;
    private StatusPage statusPage1;

    @BeforeEach
    void setUp() {
        user1 = new User("alice@example.com", "hash1", "ROLE_USER");
        user1.setId(1L);

        user2 = new User("bob@example.com", "hash2", "ROLE_USER");
        user2.setId(2L);

        monitor1 = new Monitor(user1, "API Monitor", "https://api.example.com");
        monitor1.setId(10L);

        monitor2 = new Monitor(user1, "Web Monitor", "https://web.example.com");
        monitor2.setId(20L);

        statusPage1 = new StatusPage(user1, "Acme Status", "acme", "Acme public status", true, new LinkedHashSet<>(List.of(monitor1, monitor2)));
        statusPage1.setId(100L);
    }

    @Test
    void createStatusPage_withValidDataAndMonitors_createsSuccessfully() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user1);
        when(statusPageRepository.existsBySlug("acme-corp")).thenReturn(false);
        when(monitorRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(monitor1));
        when(monitorRepository.findByIdAndUserId(20L, 1L)).thenReturn(Optional.of(monitor2));

        when(statusPageRepository.save(any(StatusPage.class))).thenAnswer(invocation -> {
            StatusPage sp = invocation.getArgument(0);
            sp.setId(101L);
            return sp;
        });

        CreateStatusPageRequest request = new CreateStatusPageRequest(
                "Acme Corp Status",
                "acme-corp",
                "Official systems status",
                true,
                List.of(10L, 20L)
        );

        StatusPageResponse response = statusPageService.createStatusPage("alice@example.com", request);

        assertNotNull(response);
        assertEquals(101L, response.id());
        assertEquals(1L, response.userId());
        assertEquals("Acme Corp Status", response.name());
        assertEquals("acme-corp", response.slug());
        assertEquals("Official systems status", response.description());
        assertTrue(response.isPublished());
        assertEquals(List.of(10L, 20L), response.monitorIds());

        ArgumentCaptor<StatusPage> captor = ArgumentCaptor.forClass(StatusPage.class);
        verify(statusPageRepository).save(captor.capture());
        StatusPage saved = captor.getValue();
        assertEquals(user1, saved.getUser());
        assertEquals(2, saved.getMonitors().size());
    }

    @Test
    void createStatusPage_withEmptyMonitors_createsSuccessfully() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user1);
        when(statusPageRepository.existsBySlug("empty-monitors")).thenReturn(false);

        when(statusPageRepository.save(any(StatusPage.class))).thenAnswer(invocation -> {
            StatusPage sp = invocation.getArgument(0);
            sp.setId(102L);
            return sp;
        });

        CreateStatusPageRequest request = new CreateStatusPageRequest(
                "Empty Status",
                "empty-monitors",
                null,
                false,
                Collections.emptyList()
        );

        StatusPageResponse response = statusPageService.createStatusPage("alice@example.com", request);

        assertNotNull(response);
        assertEquals(102L, response.id());
        assertEquals("empty-monitors", response.slug());
        assertFalse(response.isPublished());
        assertTrue(response.monitorIds().isEmpty());
    }

    @Test
    void createStatusPage_withDuplicateSlug_throwsDuplicateSlugException() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user1);
        when(statusPageRepository.existsBySlug("acme")).thenReturn(true);

        CreateStatusPageRequest request = new CreateStatusPageRequest(
                "Duplicate Acme",
                "acme",
                null,
                false,
                List.of()
        );

        DuplicateSlugException ex = assertThrows(
                DuplicateSlugException.class,
                () -> statusPageService.createStatusPage("alice@example.com", request)
        );

        assertEquals("Status page with slug already exists: acme", ex.getMessage());
        verify(statusPageRepository, never()).save(any());
    }

    @Test
    void createStatusPage_withUnownedMonitor_throwsMonitorNotFoundException() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user1);
        when(statusPageRepository.existsBySlug("test-slug")).thenReturn(false);
        when(monitorRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        CreateStatusPageRequest request = new CreateStatusPageRequest(
                "Test Status",
                "test-slug",
                null,
                false,
                List.of(99L)
        );

        MonitorNotFoundException ex = assertThrows(
                MonitorNotFoundException.class,
                () -> statusPageService.createStatusPage("alice@example.com", request)
        );

        assertEquals("Monitor not found with id: 99", ex.getMessage());
        verify(statusPageRepository, never()).save(any());
    }

    @Test
    void createStatusPage_withNonExistentMonitor_throwsMonitorNotFoundException() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user1);
        when(statusPageRepository.existsBySlug("test-slug")).thenReturn(false);
        when(monitorRepository.findByIdAndUserId(999999L, 1L)).thenReturn(Optional.empty());

        CreateStatusPageRequest request = new CreateStatusPageRequest(
                "Test Status",
                "test-slug",
                null,
                false,
                List.of(999999L)
        );

        MonitorNotFoundException ex = assertThrows(
                MonitorNotFoundException.class,
                () -> statusPageService.createStatusPage("alice@example.com", request)
        );

        assertEquals("Monitor not found with id: 999999", ex.getMessage());
        verify(statusPageRepository, never()).save(any());
    }

    @Test
    void createStatusPage_withInvalidMonitorId_throwsIllegalArgumentException() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user1);
        when(statusPageRepository.existsBySlug("test-slug")).thenReturn(false);

        CreateStatusPageRequest request = new CreateStatusPageRequest(
                "Test Status",
                "test-slug",
                null,
                false,
                List.of(-1L)
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> statusPageService.createStatusPage("alice@example.com", request)
        );

        assertTrue(ex.getMessage().contains("Monitor id must be a positive number"));
        verify(statusPageRepository, never()).save(any());
    }

    @Test
    void getUserStatusPages_returnsOnlyCurrentUsersStatusPages() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user1);
        when(statusPageRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(statusPage1));

        List<StatusPageResponse> responses = statusPageService.getUserStatusPages("alice@example.com");

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("acme", responses.get(0).slug());
        verify(statusPageRepository).findByUserIdOrderByCreatedAtDesc(1L);
    }

    @Test
    void getStatusPage_whenOwned_returnsStatusPage() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user1);
        when(statusPageRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(statusPage1));

        StatusPageResponse response = statusPageService.getStatusPage(100L, "alice@example.com");

        assertNotNull(response);
        assertEquals(100L, response.id());
        assertEquals("acme", response.slug());
    }

    @Test
    void getStatusPage_whenUnownedOrNotFound_throwsStatusPageNotFoundException() {
        when(userService.getUserByEmail("bob@example.com")).thenReturn(user2);
        when(statusPageRepository.findByIdAndUserId(100L, 2L)).thenReturn(Optional.empty());

        StatusPageNotFoundException ex = assertThrows(
                StatusPageNotFoundException.class,
                () -> statusPageService.getStatusPage(100L, "bob@example.com")
        );

        assertEquals("Status page not found with id: 100", ex.getMessage());
    }

    @Test
    void updateStatusPage_whenOwnedAndValid_updatesSuccessfully() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user1);
        when(statusPageRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(statusPage1));
        when(statusPageRepository.findBySlug("acme-updated")).thenReturn(Optional.empty());
        when(monitorRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(monitor1));
        when(statusPageRepository.save(any(StatusPage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateStatusPageRequest request = new UpdateStatusPageRequest(
                "Acme Updated",
                "acme-updated",
                "Updated description",
                false,
                List.of(10L)
        );

        StatusPageResponse response = statusPageService.updateStatusPage(100L, "alice@example.com", request);

        assertNotNull(response);
        assertEquals("Acme Updated", response.name());
        assertEquals("acme-updated", response.slug());
        assertEquals("Updated description", response.description());
        assertFalse(response.isPublished());
        assertEquals(List.of(10L), response.monitorIds());
    }

    @Test
    void updateStatusPage_withSameSlug_updatesSuccessfully() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user1);
        when(statusPageRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(statusPage1));
        when(statusPageRepository.findBySlug("acme")).thenReturn(Optional.of(statusPage1));
        when(statusPageRepository.save(any(StatusPage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateStatusPageRequest request = new UpdateStatusPageRequest(
                "Acme Renamed",
                "acme",
                "Same slug description",
                true,
                Collections.emptyList()
        );

        StatusPageResponse response = statusPageService.updateStatusPage(100L, "alice@example.com", request);

        assertNotNull(response);
        assertEquals("Acme Renamed", response.name());
        assertEquals("acme", response.slug());
        assertTrue(response.monitorIds().isEmpty());
    }

    @Test
    void updateStatusPage_withDuplicateSlugOnDifferentPage_throwsDuplicateSlugException() {
        StatusPage otherPage = new StatusPage(user1, "Other Page", "other-slug", null, false, Set.of());
        otherPage.setId(200L);

        when(userService.getUserByEmail("alice@example.com")).thenReturn(user1);
        when(statusPageRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(statusPage1));
        when(statusPageRepository.findBySlug("other-slug")).thenReturn(Optional.of(otherPage));

        UpdateStatusPageRequest request = new UpdateStatusPageRequest(
                "Acme Stolen Slug",
                "other-slug",
                null,
                true,
                List.of()
        );

        DuplicateSlugException ex = assertThrows(
                DuplicateSlugException.class,
                () -> statusPageService.updateStatusPage(100L, "alice@example.com", request)
        );

        assertEquals("Status page with slug already exists: other-slug", ex.getMessage());
        verify(statusPageRepository, never()).save(any());
    }

    @Test
    void updateStatusPage_withUnownedMonitor_throwsMonitorNotFoundException() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user1);
        when(statusPageRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(statusPage1));
        when(statusPageRepository.findBySlug("acme")).thenReturn(Optional.of(statusPage1));
        when(monitorRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        UpdateStatusPageRequest request = new UpdateStatusPageRequest(
                "Acme Page",
                "acme",
                null,
                true,
                List.of(99L)
        );

        MonitorNotFoundException ex = assertThrows(
                MonitorNotFoundException.class,
                () -> statusPageService.updateStatusPage(100L, "alice@example.com", request)
        );

        assertEquals("Monitor not found with id: 99", ex.getMessage());
        verify(statusPageRepository, never()).save(any());
    }

    @Test
    void updateStatusPage_whenUnownedOrNotFound_throwsStatusPageNotFoundException() {
        when(userService.getUserByEmail("bob@example.com")).thenReturn(user2);
        when(statusPageRepository.findByIdAndUserId(100L, 2L)).thenReturn(Optional.empty());

        UpdateStatusPageRequest request = new UpdateStatusPageRequest(
                "Bob Page",
                "bob-page",
                null,
                true,
                List.of()
        );

        StatusPageNotFoundException ex = assertThrows(
                StatusPageNotFoundException.class,
                () -> statusPageService.updateStatusPage(100L, "bob@example.com", request)
        );

        assertEquals("Status page not found with id: 100", ex.getMessage());
        verify(statusPageRepository, never()).save(any());
    }

    @Test
    void deleteStatusPage_whenOwned_deletesSuccessfully() {
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user1);
        when(statusPageRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(statusPage1));

        statusPageService.deleteStatusPage(100L, "alice@example.com");

        verify(statusPageRepository).delete(statusPage1);
    }

    @Test
    void deleteStatusPage_whenUnownedOrNotFound_throwsStatusPageNotFoundException() {
        when(userService.getUserByEmail("bob@example.com")).thenReturn(user2);
        when(statusPageRepository.findByIdAndUserId(100L, 2L)).thenReturn(Optional.empty());

        StatusPageNotFoundException ex = assertThrows(
                StatusPageNotFoundException.class,
                () -> statusPageService.deleteStatusPage(100L, "bob@example.com")
        );

        assertEquals("Status page not found with id: 100", ex.getMessage());
        verify(statusPageRepository, never()).delete(any());
    }
}

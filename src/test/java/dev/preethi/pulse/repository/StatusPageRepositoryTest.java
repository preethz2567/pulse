package dev.preethi.pulse.repository;

import dev.preethi.pulse.entity.Monitor;
import dev.preethi.pulse.entity.StatusPage;
import dev.preethi.pulse.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatusPageRepositoryTest {

    @Mock
    private StatusPageRepository statusPageRepository;

    private User user;
    private Monitor monitor;
    private StatusPage statusPage;

    @BeforeEach
    void setUp() {
        user = new User("alice@example.com", "hash", "ROLE_USER");
        user.setId(1L);

        monitor = new Monitor(user, "Auth Service", "https://auth.example.com");
        monitor.setId(10L);

        statusPage = new StatusPage(user, "Acme Status", "acme", "Public status page", true, new LinkedHashSet<>(Set.of(monitor)));
        statusPage.setId(100L);
    }

    @Test
    void findByUserIdOrderByCreatedAtDesc_returnsUserStatusPages() {
        when(statusPageRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(statusPage));

        List<StatusPage> result = statusPageRepository.findByUserIdOrderByCreatedAtDesc(1L);

        assertEquals(1, result.size());
        assertEquals("acme", result.get(0).getSlug());
        verify(statusPageRepository).findByUserIdOrderByCreatedAtDesc(1L);
    }

    @Test
    void findByIdAndUserId_whenMatches_returnsStatusPage() {
        when(statusPageRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(statusPage));

        Optional<StatusPage> result = statusPageRepository.findByIdAndUserId(100L, 1L);

        assertTrue(result.isPresent());
        assertEquals("Acme Status", result.get().getName());
        verify(statusPageRepository).findByIdAndUserId(100L, 1L);
    }

    @Test
    void findByIdAndUserId_whenNotOwned_returnsEmpty() {
        when(statusPageRepository.findByIdAndUserId(100L, 2L)).thenReturn(Optional.empty());

        Optional<StatusPage> result = statusPageRepository.findByIdAndUserId(100L, 2L);

        assertFalse(result.isPresent());
        verify(statusPageRepository).findByIdAndUserId(100L, 2L);
    }

    @Test
    void findBySlug_whenMatches_returnsStatusPage() {
        when(statusPageRepository.findBySlug("acme")).thenReturn(Optional.of(statusPage));

        Optional<StatusPage> result = statusPageRepository.findBySlug("acme");

        assertTrue(result.isPresent());
        assertEquals(100L, result.get().getId());
        verify(statusPageRepository).findBySlug("acme");
    }

    @Test
    void existsBySlug_returnsBoolean() {
        when(statusPageRepository.existsBySlug("acme")).thenReturn(true);
        when(statusPageRepository.existsBySlug("unknown")).thenReturn(false);

        assertTrue(statusPageRepository.existsBySlug("acme"));
        assertFalse(statusPageRepository.existsBySlug("unknown"));

        verify(statusPageRepository).existsBySlug("acme");
        verify(statusPageRepository).existsBySlug("unknown");
    }
}

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class StatusPageService {

    private final StatusPageRepository statusPageRepository;
    private final MonitorRepository monitorRepository;
    private final UserService userService;
    private final PublicStatusCache publicStatusCache;

    public StatusPageService(
            StatusPageRepository statusPageRepository,
            MonitorRepository monitorRepository,
            UserService userService
    ) {
        this(statusPageRepository, monitorRepository, userService, new PublicStatusCache());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public StatusPageService(
            StatusPageRepository statusPageRepository,
            MonitorRepository monitorRepository,
            UserService userService,
            PublicStatusCache publicStatusCache
    ) {
        this.statusPageRepository = statusPageRepository;
        this.monitorRepository = monitorRepository;
        this.userService = userService;
        this.publicStatusCache = publicStatusCache != null ? publicStatusCache : new PublicStatusCache();
    }

    @Transactional
    public StatusPageResponse createStatusPage(String userEmail, CreateStatusPageRequest request) {
        User user = userService.getUserByEmail(userEmail);

        if (statusPageRepository.existsBySlug(request.slug())) {
            throw new DuplicateSlugException("Status page with slug already exists: " + request.slug());
        }

        Set<Monitor> monitors = validateAndResolveMonitors(request.monitorIds(), user.getId());

        StatusPage statusPage = new StatusPage(
                user,
                request.name(),
                request.slug(),
                request.description(),
                request.isPublished(),
                monitors
        );

        StatusPage saved = statusPageRepository.save(statusPage);
        return StatusPageResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<StatusPageResponse> getUserStatusPages(String userEmail) {
        User user = userService.getUserByEmail(userEmail);
        return statusPageRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(StatusPageResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public StatusPageResponse getStatusPage(Long id, String userEmail) {
        User user = userService.getUserByEmail(userEmail);
        StatusPage statusPage = statusPageRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new StatusPageNotFoundException("Status page not found with id: " + id));
        return StatusPageResponse.from(statusPage);
    }

    @Transactional
    public StatusPageResponse updateStatusPage(Long id, String userEmail, UpdateStatusPageRequest request) {
        User user = userService.getUserByEmail(userEmail);
        StatusPage statusPage = statusPageRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new StatusPageNotFoundException("Status page not found with id: " + id));

        Optional<StatusPage> existingWithSlug = statusPageRepository.findBySlug(request.slug());
        if (existingWithSlug.isPresent() && !existingWithSlug.get().getId().equals(id)) {
            throw new DuplicateSlugException("Status page with slug already exists: " + request.slug());
        }

        String oldSlug = statusPage.getSlug();

        Set<Monitor> monitors = validateAndResolveMonitors(request.monitorIds(), user.getId());

        statusPage.setName(request.name());
        statusPage.setSlug(request.slug());
        statusPage.setDescription(request.description());
        statusPage.setIsPublished(request.isPublished());
        statusPage.setMonitors(monitors);

        StatusPage updated = statusPageRepository.save(statusPage);

        evictCache(oldSlug);
        evictCache(request.slug());

        return StatusPageResponse.from(updated);
    }

    @Transactional
    public void deleteStatusPage(Long id, String userEmail) {
        User user = userService.getUserByEmail(userEmail);
        StatusPage statusPage = statusPageRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new StatusPageNotFoundException("Status page not found with id: " + id));

        String slug = statusPage.getSlug();
        statusPageRepository.delete(statusPage);
        evictCache(slug);
    }

    private void evictCache(String slug) {
        if (slug == null || slug.isBlank()) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publicStatusCache.evict(slug);
                }
            });
        } else {
            publicStatusCache.evict(slug);
        }
    }

    private Set<Monitor> validateAndResolveMonitors(List<Long> monitorIds, Long userId) {
        Set<Monitor> monitors = new LinkedHashSet<>();
        if (monitorIds != null && !monitorIds.isEmpty()) {
            for (Long monitorId : monitorIds) {
                if (monitorId == null || monitorId <= 0) {
                    throw new IllegalArgumentException("Monitor id must be a positive number: " + monitorId);
                }
                Monitor monitor = monitorRepository.findByIdAndUserId(monitorId, userId)
                        .orElseThrow(() -> new MonitorNotFoundException("Monitor not found with id: " + monitorId));
                monitors.add(monitor);
            }
        }
        return monitors;
    }
}

package dev.preethi.pulse.service;

import dev.preethi.pulse.dto.PublicStatusPageResponse;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PublicStatusCache {

    public static final Duration TTL = Duration.ofSeconds(30);

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Clock clock;

    public PublicStatusCache() {
        this(Clock.systemUTC());
    }

    public PublicStatusCache(Clock clock) {
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public Optional<PublicStatusPageResponse> get(String slug) {
        if (slug == null) {
            return Optional.empty();
        }
        CacheEntry entry = cache.get(slug);
        if (entry == null) {
            return Optional.empty();
        }
        if (Instant.now(clock).isAfter(entry.expiresAt())) {
            cache.remove(slug);
            return Optional.empty();
        }
        return Optional.of(entry.response());
    }

    public void put(String slug, PublicStatusPageResponse response) {
        if (slug != null && response != null) {
            Instant expiresAt = Instant.now(clock).plus(TTL);
            cache.put(slug, new CacheEntry(response, expiresAt));
        }
    }

    public void evict(String slug) {
        if (slug != null) {
            cache.remove(slug);
        }
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

    record CacheEntry(PublicStatusPageResponse response, Instant expiresAt) {}
}

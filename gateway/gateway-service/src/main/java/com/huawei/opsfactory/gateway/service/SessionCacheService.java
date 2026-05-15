/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Short-lived cache for aggregated session lists, keyed by userId.
 * Reduces repeated full-fetches from all goosed instances during pagination.
 */
@Service
public class SessionCacheService {
    private static final Logger log = LoggerFactory.getLogger(SessionCacheService.class);

    private static final long DEFAULT_TTL_MS = 30_000;

    private static final int MAX_ENTRIES = 500;

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * Returns cached sessions for the given user ID if still within the TTL.
     *
     * @param userId user identifier whose cached sessions to retrieve
     * @return cached session list, or null if expired or absent
     */
    public List<Map<String, Object>> get(String userId) {
        CacheEntry entry = cache.get(userId);
        if (entry == null || System.currentTimeMillis() - entry.timestamp > DEFAULT_TTL_MS) {
            return null;
        }
        return entry.sessions;
    }

    /**
     * Atomically cache the supplied data or return already-cached data from a concurrent request.
     * Prevents concurrent cache misses from duplicating the expensive fetch.
     *
     * @param userId user identifier to cache sessions for
     * @param loader supplier that fetches sessions when the cache is empty or expired
     * @return the cached or freshly loaded session list
     */
    public List<Map<String, Object>> getOrFetch(String userId, Supplier<List<Map<String, Object>>> loader) {
        CacheEntry existing = cache.get(userId);
        if (existing != null && System.currentTimeMillis() - existing.timestamp <= DEFAULT_TTL_MS) {
            return existing.sessions;
        }
        if (cache.size() >= MAX_ENTRIES) {
            cache.clear();
            log.info("[SESSION-CACHE] size limit reached, cleared all entries");
        }
        List<Map<String, Object>> loaded = List.copyOf(loader.get());
        CacheEntry newEntry = new CacheEntry(loaded, System.currentTimeMillis());
        return cache.compute(userId, (k, prev) -> {
            if (prev != null && prev != existing && System.currentTimeMillis() - prev.timestamp <= DEFAULT_TTL_MS) {
                return prev;
            }
            log.debug("[SESSION-CACHE] cached userId={} count={}", k, loaded.size());
            return newEntry;
        }).sessions;
    }

    /**
     * Invalidates the cached sessions for the given user ID.
     *
     * @param userId user identifier whose cache entry to remove
     */
    public void invalidate(String userId) {
        cache.remove(userId);
        log.debug("[SESSION-CACHE] invalidated userId={}", userId);
    }

    /**
     * Periodically removes expired cache entries.
     */
    @Scheduled(fixedRate = 60_000)
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> now - e.getValue().timestamp > DEFAULT_TTL_MS);
    }

    private record CacheEntry(List<Map<String, Object>> sessions, long timestamp) {
    }
}

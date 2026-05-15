/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.monitoring;

import com.huawei.opsfactory.gateway.config.GatewayProperties;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Circular buffer for metrics snapshots and request timings.
 * Persists to a JSON file under gateway/data/monitoring/ for restart resilience.
 * <p>
 * 120 snapshot slots × 30s = 1 hour rolling window.
 * 500 request timing slots for per-request latency capture.
 * </p>
 */
@Component
public class MetricsBuffer {
    private static final Logger log = LoggerFactory.getLogger(MetricsBuffer.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int SNAPSHOT_CAPACITY = 120;

    private static final int TIMING_CAPACITY = 500;

    private static final long ONE_HOUR_MS = 3_600_000L;

    private final MetricsSnapshot[] snapshots = new MetricsSnapshot[SNAPSHOT_CAPACITY];

    private final RequestTiming[] timings = new RequestTiming[TIMING_CAPACITY];

    // Per-agent stats accumulated over the 1-hour window
    private final ConcurrentHashMap<String, AgentStats> agentStatsMap = new ConcurrentHashMap<>();

    private final Path persistPath;

    private int snapshotWriteIndex = 0;

    private int snapshotCount = 0;

    private boolean dirty = false;

    private int timingWriteIndex = 0;

    private int pendingTimingCount = 0;

    /**
     * Creates the metrics buffer instance.
     *
     * @param properties gateway configuration properties
     */
    public MetricsBuffer(GatewayProperties properties) {
        Path gatewayRoot = properties.getGatewayRootPath();
        this.persistPath = gatewayRoot.resolve("data").resolve("monitoring").resolve("metrics.json");
        loadFromDisk();
    }

    /**
     * Record a collected metrics snapshot.
     */
    public synchronized void record(MetricsSnapshot snapshot) {
        snapshots[snapshotWriteIndex] = snapshot;
        snapshotWriteIndex = (snapshotWriteIndex + 1) % SNAPSHOT_CAPACITY;
        if (snapshotCount < SNAPSHOT_CAPACITY) {
            snapshotCount++;
        }
        dirty = true;
    }

    /**
     * Record a single request timing (called from SSE relay threads).
     * Uses a separate lock object to avoid contention with snapshot operations.
     *
     * @param timing request timing sample to record
     */
    public synchronized void recordTiming(RequestTiming timing) {
        timings[timingWriteIndex] = timing;
        timingWriteIndex = (timingWriteIndex + 1) % TIMING_CAPACITY;
        if (pendingTimingCount < TIMING_CAPACITY) {
            pendingTimingCount++;
        }
        // Accumulate per-agent stats
        String agentId = timing.getAgentId();
        if (agentId != null) {
            agentStatsMap.compute(agentId, (k, stats) -> {
                if (stats == null) {
                    stats = new AgentStats();
                }
                stats.record(timing.getTotalMs(), timing.getTtftMs(), timing.isError());
                return stats;
            });
        }
    }

    /**
     * Get per-agent statistics accumulated over the buffer lifetime.
     *
     * @return map of agent ID to stats including requestCount, errorCount, avgLatencyMs, and avgTtftMs
     */
    public Map<String, Map<String, Object>> getAgentStats() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (var entry : agentStatsMap.entrySet()) {
            AgentStats s = entry.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("requestCount", s.getRequestCount());
            m.put("errorCount", s.getErrorCount());
            m.put("avgLatencyMs", Math.round(s.getAvgLatencyMs() * 100.0) / 100.0);
            m.put("avgTtftMs", Math.round(s.getAvgTtftMs() * 100.0) / 100.0);
            result.put(entry.getKey(), m);
        }
        return result;
    }

    /**
     * Drain all request timings recorded since the last drain.
     * Uses pendingTimingCount to correctly handle buffer wrap-around.
     *
     * @return list of request timings recorded since the last drain
     */
    public synchronized List<RequestTiming> drainTimings() {
        List<RequestTiming> result = new ArrayList<>();
        if (pendingTimingCount == 0) {
            return result;
        }

        int count = pendingTimingCount;
        int start = (timingWriteIndex - count + TIMING_CAPACITY) % TIMING_CAPACITY;
        for (int i = 0; i < count; i++) {
            int idx = (start + i) % TIMING_CAPACITY;
            RequestTiming t = timings[idx];
            if (t != null) {
                result.add(t);
            }
        }
        pendingTimingCount = 0;
        return result;
    }

    /**
     * Get the most recent snapshots, ordered oldest-first (for charting).
     *
     * @param maxSlots maximum number of snapshots to return
     * @return list of recent snapshots ordered oldest-first
     */
    public synchronized List<MetricsSnapshot> getSnapshots(int maxSlots) {
        int count = Math.min(this.snapshotCount, maxSlots);
        List<MetricsSnapshot> result = new ArrayList<>(count);
        int start = (snapshotWriteIndex - count + SNAPSHOT_CAPACITY) % SNAPSHOT_CAPACITY;
        for (int i = 0; i < count; i++) {
            int idx = (start + i) % SNAPSHOT_CAPACITY;
            MetricsSnapshot s = snapshots[idx];
            if (s != null) {
                result.add(s);
            }
        }
        return result;
    }

    /**
     * Persist current snapshots to disk as JSON.
     * Skips write if nothing changed since last persist.
     * Performs I/O outside the lock to avoid blocking SSE relay threads.
     */
    public void persistToDisk() {
        List<MetricsSnapshot> toWrite;
        synchronized (this) {
            if (!dirty) {
                return;
            }
            toWrite = getSnapshots(SNAPSHOT_CAPACITY);
            dirty = false;
        }
        // I/O outside lock
        try {
            Files.createDirectories(persistPath.getParent());
            Map<String, Object> wrapper =
                Map.of("version", 1, "updatedAt", System.currentTimeMillis(), "snapshots", toWrite);
            MAPPER.writeValue(persistPath.toFile(), wrapper);
        } catch (IOException e) {
            log.warn("Failed to persist metrics to {}: {}", persistPath, e.getMessage());
        }
    }

    /**
     * Load persisted snapshots from disk on startup.
     * Discards entries older than 1 hour.
     */
    private void loadFromDisk() {
        try {
            if (!Files.exists(persistPath)) {
                log.info("No persisted metrics file at {}", persistPath);
                return;
            }
            Map<String, Object> wrapper =
                MAPPER.readValue(persistPath.toFile(), new TypeReference<Map<String, Object>>() {});
            Object snapshotObj = wrapper.get("snapshots");
            if (snapshotObj == null) {
                return;
            }

            List<MetricsSnapshot> loaded =
                MAPPER.convertValue(snapshotObj, new TypeReference<List<MetricsSnapshot>>() {});

            long cutoff = System.currentTimeMillis() - ONE_HOUR_MS;
            int restored = 0;
            for (MetricsSnapshot s : loaded) {
                if (s.getTimestamp() >= cutoff) {
                    record(s);
                    restored++;
                }
            }
            // Reset dirty since this is just a restore, not new data
            dirty = false;
            log.info("Restored {} metrics snapshots from {} (discarded {} stale)", restored, persistPath,
                loaded.size() - restored);
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Failed to load persisted metrics from {}: {}", persistPath, e.getMessage());
        }
    }

    /**
     * Accumulated per-agent statistics.
     *
     * @author x00000000
     * @since 2026-05-09
     */
    public static class AgentStats {
        private int requestCount;

        private int errorCount;

        private long latencySum;

        private long ttftSum;

        /**
         * Records a new request sample.
         *
         * @param totalMs total request latency in milliseconds
         * @param ttftMs time-to-first-token in milliseconds
         * @param error whether the request resulted in an error
         */
        public void record(long totalMs, long ttftMs, boolean error) {
            requestCount++;
            latencySum += totalMs;
            ttftSum += ttftMs;
            if (error) {
                errorCount++;
            }
        }

        /**
         * Gets the request count.
         *
         * @return total number of recorded requests
         */
        public int getRequestCount() {
            return requestCount;
        }

        /**
         * Gets the error count.
         *
         * @return number of requests that resulted in an error
         */
        public int getErrorCount() {
            return errorCount;
        }

        /**
         * Gets the average request latency in milliseconds.
         *
         * @return average request latency in milliseconds
         */
        public double getAvgLatencyMs() {
            return requestCount > 0 ? (double) latencySum / requestCount : 0;
        }

        /**
         * Gets the average time-to-first-token in milliseconds.
         *
         * @return average time-to-first-token in milliseconds
         */
        public double getAvgTtftMs() {
            return requestCount > 0 ? (double) ttftSum / requestCount : 0;
        }
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.monitoring;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.gateway.common.model.ManagedInstance;
import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.process.InstanceManager;
import com.huawei.opsfactory.gateway.proxy.GoosedProxy;

import reactor.core.publisher.Mono;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Test coverage for Metrics Collector.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class MetricsCollectorTest {
    private InstanceManager instanceManager;

    private GoosedProxy goosedProxy;

    private MetricsBuffer metricsBuffer;

    private MetricsCollector collector;

    /**
     * Sets the up.
     */
    @Before
    public void setUp() {
        instanceManager = mock(InstanceManager.class);
        goosedProxy = mock(GoosedProxy.class);

        GatewayProperties props = new GatewayProperties();
        props.getPaths()
            .setProjectRoot(System.getProperty("java.io.tmpdir") + "/metrics-collector-test-" + System.nanoTime());
        metricsBuffer = new MetricsBuffer(props);

        collector = new MetricsCollector(instanceManager, goosedProxy, metricsBuffer);
    }

    /**
     * Tests collect no running instances.
     */
    @Test
    public void testCollect_noRunningInstances() {
        when(instanceManager.getAllInstances()).thenReturn(Collections.emptyList());

        collector.collect();

        List<MetricsSnapshot> snapshots = metricsBuffer.getSnapshots(10);
        assertEquals(1, snapshots.size());
        MetricsSnapshot s = snapshots.get(0);
        assertEquals(0, s.getActiveInstances());
        assertEquals(0, s.getTotalTokens());
        assertEquals(0, s.getRequestCount());
    }

    /**
     * Tests collect with running instances.
     */
    @Test
    public void testCollect_withRunningInstances() {
        ManagedInstance inst1 = createRunningInstance(8001);
        ManagedInstance inst2 = createRunningInstance(8002);
        when(instanceManager.getAllInstances()).thenReturn(Arrays.asList(inst1, inst2));

        when(goosedProxy.fetchJson(eq(8001), eq("/sessions/insights"), anyString()))
            .thenReturn(Mono.just("{\"total_tokens\": 100, \"total_sessions\": 2}"));
        when(goosedProxy.fetchJson(eq(8002), eq("/sessions/insights"), anyString()))
            .thenReturn(Mono.just("{\"total_tokens\": 200, \"total_sessions\": 3}"));

        collector.collect();

        List<MetricsSnapshot> snapshots = metricsBuffer.getSnapshots(10);
        assertEquals(1, snapshots.size());
        MetricsSnapshot s = snapshots.get(0);
        assertEquals(2, s.getActiveInstances());
        assertEquals(300, s.getTotalTokens());
        assertEquals(5, s.getTotalSessions());
    }

    /**
     * Tests collect instance fetch error gracefully handled.
     */
    @Test
    public void testCollect_instanceFetchError_gracefullyHandled() {
        ManagedInstance inst = createRunningInstance(8001);
        when(instanceManager.getAllInstances()).thenReturn(Collections.singletonList(inst));

        when(goosedProxy.fetchJson(eq(8001), eq("/sessions/insights"), anyString()))
            .thenReturn(Mono.error(new RuntimeException("connection refused")));

        collector.collect();

        // Should still record a snapshot with zero tokens
        List<MetricsSnapshot> snapshots = metricsBuffer.getSnapshots(10);
        assertEquals(1, snapshots.size());
        assertEquals(0, snapshots.get(0).getTotalTokens());
    }

    /**
     * Tests collect tokens per sec computed on second call.
     */
    @Test
    public void testCollect_tokensPerSec_computedOnSecondCall() {
        ManagedInstance inst = createRunningInstance(8001);
        when(instanceManager.getAllInstances()).thenReturn(Collections.singletonList(inst));

        // First call: 100 tokens
        when(goosedProxy.fetchJson(eq(8001), eq("/sessions/insights"), anyString()))
            .thenReturn(Mono.just("{\"total_tokens\": 100, \"total_sessions\": 1}"));
        collector.collect();

        // Second call: 400 tokens (delta = 300, interval = 30s, so 10 tok/s)
        when(goosedProxy.fetchJson(eq(8001), eq("/sessions/insights"), anyString()))
            .thenReturn(Mono.just("{\"total_tokens\": 400, \"total_sessions\": 2}"));
        collector.collect();

        List<MetricsSnapshot> snapshots = metricsBuffer.getSnapshots(10);
        assertEquals(2, snapshots.size());
        MetricsSnapshot first = snapshots.get(0);
        MetricsSnapshot second = snapshots.get(1);

        // First snapshot: no previous data, tokensPerSec = 0
        assertEquals(0.0, first.getTokensPerSec(), 0.001);
        // Second snapshot: delta=300 over 30s = 10 tok/s
        assertEquals(10.0, second.getTokensPerSec(), 0.001);
    }

    /**
     * Tests collect with request timings latency stats.
     */
    @Test
    public void testCollect_withRequestTimings_latencyStats() {
        when(instanceManager.getAllInstances()).thenReturn(Collections.emptyList());

        // Add some request timings
        metricsBuffer
            .recordTiming(new RequestTiming(System.currentTimeMillis(), 50, 200, 1024, false, "agent1", "user1"));
        metricsBuffer
            .recordTiming(new RequestTiming(System.currentTimeMillis(), 100, 400, 2048, false, "agent1", "user2"));
        metricsBuffer
            .recordTiming(new RequestTiming(System.currentTimeMillis(), 150, 600, 512, true, "agent1", "user3"));

        collector.collect();

        List<MetricsSnapshot> snapshots = metricsBuffer.getSnapshots(10);
        assertEquals(1, snapshots.size());
        MetricsSnapshot s = snapshots.get(0);

        assertEquals(3, s.getRequestCount());
        assertEquals(1, s.getErrorCount());
        assertEquals(3584, s.getTotalBytes());

        // Avg latency = (200+400+600)/3 = 400
        assertEquals(400.0, s.getAvgLatencyMs(), 0.001);
        // Avg TTFT = (50+100+150)/3 = 100
        assertEquals(100.0, s.getAvgTtftMs(), 0.001);
        // p95 with 3 items: ceil(3*0.95)-1 = 2, so index 2 = 600
        assertEquals(600.0, s.getP95LatencyMs(), 0.001);
        assertEquals(150.0, s.getP95TtftMs(), 0.001);
    }

    /**
     * Tests collect with single timing.
     */
    @Test
    public void testCollect_withSingleTiming() {
        when(instanceManager.getAllInstances()).thenReturn(Collections.emptyList());

        metricsBuffer
            .recordTiming(new RequestTiming(System.currentTimeMillis(), 80, 300, 500, false, "agent1", "user1"));

        collector.collect();

        List<MetricsSnapshot> snapshots = metricsBuffer.getSnapshots(10);
        MetricsSnapshot s = snapshots.get(0);

        assertEquals(1, s.getRequestCount());
        assertEquals(0, s.getErrorCount());
        assertEquals(300.0, s.getAvgLatencyMs(), 0.001);
        assertEquals(80.0, s.getAvgTtftMs(), 0.001);
        // p95 with 1 item = the item itself
        assertEquals(300.0, s.getP95LatencyMs(), 0.001);
        assertEquals(80.0, s.getP95TtftMs(), 0.001);
    }

    /**
     * Tests collect filters non running instances.
     */
    @Test
    public void testCollect_filtersNonRunningInstances() {
        ManagedInstance running = createRunningInstance(8001);
        ManagedInstance stopped = new ManagedInstance("agent2", "user2", 8002, 0L, null, "test-secret");
        stopped.setStatus(ManagedInstance.Status.STOPPED);

        when(instanceManager.getAllInstances()).thenReturn(Arrays.asList(running, stopped));
        when(goosedProxy.fetchJson(eq(8001), eq("/sessions/insights"), anyString()))
            .thenReturn(Mono.just("{\"total_tokens\": 50, \"total_sessions\": 1}"));

        collector.collect();

        List<MetricsSnapshot> snapshots = metricsBuffer.getSnapshots(10);
        MetricsSnapshot s = snapshots.get(0);
        // Only the running instance should be counted
        assertEquals(1, s.getActiveInstances());
        assertEquals(50, s.getTotalTokens());
    }

    /**
     * Tests collect malformed json response.
     */
    @Test
    public void testCollect_malformedJsonResponse() {
        ManagedInstance inst = createRunningInstance(8001);
        when(instanceManager.getAllInstances()).thenReturn(Collections.singletonList(inst));

        when(goosedProxy.fetchJson(eq(8001), eq("/sessions/insights"), anyString()))
            .thenReturn(Mono.just("not json at all"));

        collector.collect();

        // Should handle gracefully with zero tokens
        List<MetricsSnapshot> snapshots = metricsBuffer.getSnapshots(10);
        assertEquals(1, snapshots.size());
        assertEquals(0, snapshots.get(0).getTotalTokens());
    }

    private ManagedInstance createRunningInstance(int port) {
        ManagedInstance inst = new ManagedInstance("agent1", "user1", port, 0L, null, "test-secret");
        inst.setStatus(ManagedInstance.Status.RUNNING);
        return inst;
    }
}

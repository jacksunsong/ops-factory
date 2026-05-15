/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.process;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.gateway.common.model.ManagedInstance;
import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.service.AgentConfigService;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * Test coverage for Instance Watchdog.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class InstanceWatchdogTest {
    private InstanceManager instanceManager;

    private GatewayProperties properties;

    private PrewarmService prewarmService;

    private AgentConfigService agentConfigService;

    private InstanceWatchdog watchdog;

    /**
     * Sets the up.
     */
    @Before
    public void setUp() {
        instanceManager = mock(InstanceManager.class);
        properties = new GatewayProperties();
        properties.getIdle().setTimeoutMinutes(15);
        properties.getIdle().setMaxRestartAttempts(3);
        properties.getIdle().setRestartBaseDelayMs(5000L);
        prewarmService = mock(PrewarmService.class);
        agentConfigService = mock(AgentConfigService.class);
        watchdog = new InstanceWatchdog(instanceManager, properties, prewarmService, agentConfigService);
    }

    // ---- Idle reap tests (original behavior) ----

    /**
     * Tests reap idle instance.
     */
    @Test
    public void testReap_idleInstance() {
        ManagedInstance idle = createInstance("agent1", "user1", ManagedInstance.Status.RUNNING, mockAliveProcess());
        setLastActivity(idle, System.currentTimeMillis() - 20 * 60 * 1000L);

        when(instanceManager.getAllInstances()).thenReturn(List.of(idle));

        watchdog.watchInstances();

        verify(instanceManager).stopInstance(idle);
    }

    /**
     * Tests reap active instance.
     */
    @Test
    public void testReap_activeInstance() {
        ManagedInstance active = createInstance("agent1", "user1", ManagedInstance.Status.RUNNING, mockAliveProcess());
        setLastActivity(active, System.currentTimeMillis());

        when(instanceManager.getAllInstances()).thenReturn(List.of(active));

        watchdog.watchInstances();

        verify(instanceManager, never()).stopInstance(active);
    }

    /**
     * Tests reap never reaps resident instance.
     */
    @Test
    public void testReap_neverReapsResidentInstance() {
        ManagedInstance resident =
            createInstance("agent1", "admin", ManagedInstance.Status.RUNNING, mockAliveProcess());
        setLastActivity(resident, System.currentTimeMillis() - 60 * 60 * 1000L);

        when(instanceManager.getAllInstances()).thenReturn(List.of(resident));
        when(agentConfigService.isResidentInstance("agent1", "admin")).thenReturn(true);

        watchdog.watchInstances();

        verify(instanceManager, never()).stopInstance(resident);
    }

    /**
     * Tests reap skips non running.
     */
    @Test
    public void testReap_skipsNonRunning() {
        ManagedInstance stopped = createInstance("agent1", "user1", ManagedInstance.Status.STOPPED, null);
        setLastActivity(stopped, System.currentTimeMillis() - 20 * 60 * 1000L);

        when(instanceManager.getAllInstances()).thenReturn(List.of(stopped));

        watchdog.watchInstances();

        verify(instanceManager, never()).stopInstance(stopped);
    }

    // ---- Health check tests (new behavior) ----

    /**
     * Tests watchdog detects dead process respawns.
     */
    @Test
    public void testWatchdog_detectsDeadProcess_respawns() {
        Process deadProcess = mockDeadProcess(1);
        ManagedInstance dead = createInstance("agent1", "user1", ManagedInstance.Status.RUNNING, deadProcess);

        when(instanceManager.getAllInstances()).thenReturn(List.of(dead));

        watchdog.watchInstances();

        verify(instanceManager).stopInstance(dead);
        verify(instanceManager).respawnAsync("agent1", "user1", 1);
    }

    /**
     * Tests watchdog respects max restart attempts.
     */
    @Test
    public void testWatchdog_respectsMaxRestartAttempts() {
        Process deadProcess = mockDeadProcess(1);
        ManagedInstance dead = createInstance("agent1", "user1", ManagedInstance.Status.RUNNING, deadProcess);
        // Already at max
        dead.setRestartCount(3);

        when(instanceManager.getAllInstances()).thenReturn(List.of(dead));

        watchdog.watchInstances();

        verify(instanceManager).stopInstance(dead);
        verify(instanceManager, never()).respawnAsync("agent1", "user1", 4);
    }

    /**
     * Tests watchdog backoff delay.
     */
    @Test
    public void testWatchdog_backoffDelay() {
        Process deadProcess = mockDeadProcess(1);
        ManagedInstance dead = createInstance("agent1", "user1", ManagedInstance.Status.RUNNING, deadProcess);
        dead.setRestartCount(1);
        // Only 1s ago, backoff is 10s
        dead.setLastRestartTime(System.currentTimeMillis() - 1000);

        when(instanceManager.getAllInstances()).thenReturn(List.of(dead));

        watchdog.watchInstances();

        verify(instanceManager).stopInstance(dead);
        // Should not respawn yet due to backoff
        verify(instanceManager, never()).respawnAsync("agent1", "user1", 2);
    }

    /**
     * Tests watchdog backoff expired respawns.
     */
    @Test
    public void testWatchdog_backoffExpired_respawns() {
        Process deadProcess = mockDeadProcess(1);
        ManagedInstance dead = createInstance("agent1", "user1", ManagedInstance.Status.RUNNING, deadProcess);
        dead.setRestartCount(1);
        // 20s ago, backoff is 10s
        dead.setLastRestartTime(System.currentTimeMillis() - 20_000);

        when(instanceManager.getAllInstances()).thenReturn(List.of(dead));

        watchdog.watchInstances();

        verify(instanceManager).stopInstance(dead);
        verify(instanceManager).respawnAsync("agent1", "user1", 2);
    }

    /**
     * Tests watchdog alive process no action.
     */
    @Test
    public void testWatchdog_aliveProcess_noAction() {
        ManagedInstance alive = createInstance("agent1", "user1", ManagedInstance.Status.RUNNING, mockAliveProcess());

        when(instanceManager.getAllInstances()).thenReturn(List.of(alive));

        watchdog.watchInstances();

        verify(instanceManager, never()).stopInstance(alive);
        verify(instanceManager, never()).respawnAsync("agent1", "user1", 1);
    }

    /**
     * Tests watchdog resident dead process still respawns.
     */
    @Test
    public void testWatchdog_residentDeadProcessStillRespawns() {
        Process deadProcess = mockDeadProcess(1);
        ManagedInstance resident = createInstance("agent1", "admin", ManagedInstance.Status.RUNNING, deadProcess);

        when(instanceManager.getAllInstances()).thenReturn(List.of(resident));
        when(agentConfigService.isResidentInstance("agent1", "admin")).thenReturn(true);

        watchdog.watchInstances();

        verify(instanceManager).stopInstance(resident);
        verify(instanceManager).respawnAsync("agent1", "admin", 1);
    }

    // ---- Helpers ----

    private ManagedInstance createInstance(String agentId, String userId, ManagedInstance.Status status,
        Process process) {
        ManagedInstance instance = new ManagedInstance(agentId, userId, 8080, 1234L, process, "test-secret");
        instance.setStatus(status);
        return instance;
    }

    private Process mockAliveProcess() {
        Process p = mock(Process.class);
        when(p.isAlive()).thenReturn(true);
        return p;
    }

    private Process mockDeadProcess(int exitCode) {
        Process p = mock(Process.class);
        when(p.isAlive()).thenReturn(false);
        when(p.exitValue()).thenReturn(exitCode);
        return p;
    }

    private void setLastActivity(ManagedInstance instance, long timestamp) {
        try {
            java.lang.reflect.Field field = ManagedInstance.class.getDeclaredField("lastActivity");
            field.setAccessible(true);
            field.setLong(instance, timestamp);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}

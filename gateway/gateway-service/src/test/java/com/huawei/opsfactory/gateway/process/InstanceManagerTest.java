/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.process;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.gateway.common.model.ManagedInstance;
import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.service.AgentConfigService;

import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.Map;

/**
 * Test coverage for Instance Manager.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class InstanceManagerTest {
    private InstanceManager instanceManager;

    private GatewayProperties properties;

    private PortAllocator portAllocator;

    private RuntimePreparer runtimePreparer;

    private AgentConfigService agentConfigService;

    /**
     * Sets the up.
     *
     * @throws Exception if the operation fails
     */
    @Before
    public void setUp() throws Exception {
        properties = new GatewayProperties();
        properties.setSecretKey("test-secret");
        portAllocator = mock(PortAllocator.class);
        runtimePreparer = mock(RuntimePreparer.class);
        doReturn(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "gateway-test")).when(runtimePreparer)
            .prepare(anyString(), anyString());
        agentConfigService = mock(AgentConfigService.class);
        when(agentConfigService.loadAgentConfigYaml(anyString())).thenReturn(Map.of());
        when(agentConfigService.loadAgentSecretsYaml(anyString())).thenReturn(Map.of());

        instanceManager =
            new InstanceManager(properties, portAllocator, runtimePreparer, agentConfigService, 3000, false, "");
    }

    /**
     * Tests get instance no instance.
     */
    @Test
    public void testGetInstance_noInstance() {
        assertNull(instanceManager.getInstance("agent1", "user1"));
    }

    /**
     * Tests get all instances empty.
     */
    @Test
    public void testGetAllInstances_empty() {
        Collection<ManagedInstance> all = instanceManager.getAllInstances();
        assertNotNull(all);
        assertTrue(all.isEmpty());
    }

    /**
     * Tests stop instance.
     */
    @Test
    public void testStopInstance() {
        // Create a mock instance manually with a mock process
        Process mockProcess = mock(Process.class);
        when(mockProcess.isAlive()).thenReturn(false);
        ManagedInstance instance = new ManagedInstance("agent1", "user1", 8080, 1234L, mockProcess, "test-secret");
        instance.setStatus(ManagedInstance.Status.RUNNING);

        // Use reflection to add instance to internal map for testing
        addInstanceDirectly(instance);

        assertNotNull(instanceManager.getInstance("agent1", "user1"));

        instanceManager.stopInstance(instance);

        assertNull(instanceManager.getInstance("agent1", "user1"));
        assertEquals(ManagedInstance.Status.STOPPED, instance.getStatus());
    }

    /**
     * Tests stop all for agent.
     */
    @Test
    public void testStopAllForAgent() {
        Process mockProcess = mock(Process.class);
        when(mockProcess.isAlive()).thenReturn(false);

        ManagedInstance inst1 = new ManagedInstance("agent1", "user1", 8080, 1234L, mockProcess, "test-secret");
        inst1.setStatus(ManagedInstance.Status.RUNNING);
        ManagedInstance inst2 = new ManagedInstance("agent1", "user2", 8081, 1235L, mockProcess, "test-secret");
        inst2.setStatus(ManagedInstance.Status.RUNNING);
        ManagedInstance inst3 = new ManagedInstance("agent2", "user1", 8082, 1236L, mockProcess, "test-secret");
        inst3.setStatus(ManagedInstance.Status.RUNNING);

        addInstanceDirectly(inst1);
        addInstanceDirectly(inst2);
        addInstanceDirectly(inst3);

        assertEquals(3, instanceManager.getAllInstances().size());

        instanceManager.stopAllForAgent("agent1");

        assertEquals(1, instanceManager.getAllInstances().size());
        assertNull(instanceManager.getInstance("agent1", "user1"));
        assertNull(instanceManager.getInstance("agent1", "user2"));
        assertNotNull(instanceManager.getInstance("agent2", "user1"));
    }

    /**
     * Tests touch all for user.
     *
     * @throws InterruptedException if the operation fails
     */
    @Test
    public void testTouchAllForUser() throws InterruptedException {
        Process mockProcess = mock(Process.class);

        ManagedInstance inst1 = new ManagedInstance("agent1", "user1", 8080, 1234L, mockProcess, "test-secret");
        ManagedInstance inst2 = new ManagedInstance("agent2", "user1", 8081, 1235L, mockProcess, "test-secret");
        ManagedInstance inst3 = new ManagedInstance("agent1", "user2", 8082, 1236L, mockProcess, "test-secret");

        addInstanceDirectly(inst1);
        addInstanceDirectly(inst2);
        addInstanceDirectly(inst3);

        long beforeUser2 = inst3.getLastActivity();
        Thread.sleep(10);

        instanceManager.touchAllForUser("user1");

        assertTrue(inst1.getLastActivity() > beforeUser2);
        assertTrue(inst2.getLastActivity() > beforeUser2);
        // user2's instance should NOT have been touched more recently
        assertEquals(beforeUser2, inst3.getLastActivity());
    }

    /**
     * Tests stop all.
     */
    @Test
    public void testStopAll() {
        Process mockProcess = mock(Process.class);
        when(mockProcess.isAlive()).thenReturn(false);

        ManagedInstance inst1 = new ManagedInstance("agent1", "user1", 8080, 1234L, mockProcess, "test-secret");
        inst1.setStatus(ManagedInstance.Status.RUNNING);
        ManagedInstance inst2 = new ManagedInstance("agent2", "user2", 8081, 1235L, mockProcess, "test-secret");
        inst2.setStatus(ManagedInstance.Status.RUNNING);

        addInstanceDirectly(inst1);
        addInstanceDirectly(inst2);

        instanceManager.stopAll();

        assertTrue(instanceManager.getAllInstances().isEmpty());
    }

    /**
     * Tests stop all handles errors.
     */
    @Test
    public void testStopAll_handlesErrors() {
        Process mockProcess = mock(Process.class);
        // destroyForcibly throws exception
        when(mockProcess.isAlive()).thenThrow(new RuntimeException("Process error"));

        ManagedInstance inst = new ManagedInstance("agent1", "user1", 8080, 1234L, mockProcess, "test-secret");
        inst.setStatus(ManagedInstance.Status.RUNNING);
        addInstanceDirectly(inst);

        // Should not throw even if individual instance fails
        instanceManager.stopAll();
    }

    /**
     * Tests get or spawn removes instance when process died.
     */
    @Test
    public void testGetOrSpawn_removesInstanceWhenProcessDied() {
        Process mockProcess = mock(Process.class);
        when(mockProcess.isAlive()).thenReturn(false);
        ManagedInstance existing = new ManagedInstance("agent1", "user1", 8080, 1234L, mockProcess, "test-secret");
        existing.setStatus(ManagedInstance.Status.RUNNING);
        addInstanceDirectly(existing);

        assertNotNull(instanceManager.getInstance("agent1", "user1"));

        // getOrSpawn detects the dead process, removes the stale entry, then
        // tries to spawn a new one — which fails in unit tests (no goosed binary).
        assertThrows(RuntimeException.class, () -> instanceManager.getOrSpawn("agent1", "user1").block());

        // The stale entry should have been removed
        assertNull(instanceManager.getInstance("agent1", "user1"));
    }

    /**
     * Helper to add instances directly to the internal map via reflection.
     *
     * @author x00000000
     * @since 2026-05-09
     */
    private void addInstanceDirectly(ManagedInstance instance) {
        try {
            java.lang.reflect.Field field = InstanceManager.class.getDeclaredField("instances");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.concurrent.ConcurrentHashMap<String, ManagedInstance> instances =
                (java.util.concurrent.ConcurrentHashMap<String, ManagedInstance>) field.get(instanceManager);
            instances.put(instance.getKey(), instance);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}

package com.huawei.opsfactory.gateway.process;

import com.huawei.opsfactory.gateway.common.model.ManagedInstance;
import com.huawei.opsfactory.gateway.config.GatewayProperties;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PrewarmServiceTest {

    private InstanceManager instanceManager;
    private GatewayProperties properties;
    private PrewarmService prewarmService;

    @Before
    public void setUp() {
        instanceManager = mock(InstanceManager.class);
        properties = new GatewayProperties();
        prewarmService = new PrewarmService(instanceManager, properties);
    }

    @Test
    public void testOnUserActivity_disabled_doesNotSpawn() {
        properties.getPrewarm().setEnabled(false);

        prewarmService.onUserActivity("alice");

        verify(instanceManager, never()).getOrSpawn(eq("universal-agent"), eq("alice"));
    }

    @Test
    public void testOnUserActivity_sysUser_doesNotSpawn() {
        prewarmService.onUserActivity("admin");

        verify(instanceManager, never()).getOrSpawn(eq("universal-agent"), eq("admin"));
    }

    @Test
    public void testOnUserActivity_newUser_triggersSpawn() {
        ManagedInstance instance = new ManagedInstance("universal-agent", "alice", 9000, 123L, null, "test-secret");
        when(instanceManager.getOrSpawn("universal-agent", "alice")).thenReturn(Mono.just(instance));

        prewarmService.onUserActivity("alice");

        verify(instanceManager).getOrSpawn("universal-agent", "alice");
    }

    @Test
    public void testOnUserActivity_alreadyWarmedUser_doesNotSpawnAgain() {
        ManagedInstance instance = new ManagedInstance("universal-agent", "alice", 9000, 123L, null, "test-secret");
        when(instanceManager.getOrSpawn("universal-agent", "alice")).thenReturn(Mono.just(instance));

        prewarmService.onUserActivity("alice");
        prewarmService.onUserActivity("alice");

        // Should only be called once
        verify(instanceManager).getOrSpawn("universal-agent", "alice");
    }

    @Test
    public void testClearUser_allowsRewarm() {
        ManagedInstance instance = new ManagedInstance("universal-agent", "alice", 9000, 123L, null, "test-secret");
        when(instanceManager.getOrSpawn("universal-agent", "alice")).thenReturn(Mono.just(instance));

        prewarmService.onUserActivity("alice");
        prewarmService.clearUser("alice");
        prewarmService.onUserActivity("alice");

        // Should be called twice: once before clear, once after
        verify(instanceManager, org.mockito.Mockito.times(2)).getOrSpawn("universal-agent", "alice");
    }

    @Test
    public void testOnUserActivity_customDefaultAgent() {
        properties.getPrewarm().setDefaultAgentId("kb-agent");
        // Recreate service with updated properties
        prewarmService = new PrewarmService(instanceManager, properties);

        ManagedInstance instance = new ManagedInstance("kb-agent", "bob", 9000, 123L, null, "test-secret");
        when(instanceManager.getOrSpawn("kb-agent", "bob")).thenReturn(Mono.just(instance));

        prewarmService.onUserActivity("bob");

        verify(instanceManager).getOrSpawn("kb-agent", "bob");
    }

    @Test
    public void testOnUserActivity_spawnError_doesNotThrow() {
        when(instanceManager.getOrSpawn("universal-agent", "alice"))
                .thenReturn(Mono.error(new RuntimeException("spawn failed")));

        // Should not throw — error is handled in subscribe
        prewarmService.onUserActivity("alice");
    }
}

/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.gateway.common.model.ManagedInstance;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.process.InstanceManager;
import com.huawei.opsfactory.gateway.proxy.GoosedProxy;

import reactor.core.publisher.Mono;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

/**
 * Test coverage for Catch All Proxy Controller.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class CatchAllProxyControllerTest {
    private InstanceManager instanceManager;

    private GoosedProxy goosedProxy;

    private CatchAllProxyController controller;

    /**
     * Sets the up.
     */
    @Before
    public void setUp() {
        instanceManager = mock(InstanceManager.class);
        goosedProxy = mock(GoosedProxy.class);
        controller = new CatchAllProxyController(instanceManager, goosedProxy);
    }

    /**
     * Tests authenticated user access to any route proxies.
     */
    @Test
    public void testAuthenticatedAccess_proxies() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/agents/test-agent/schedules/list").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(UserContextFilter.USER_ID_ATTR, "alice");

        ManagedInstance instance = new ManagedInstance("test-agent", "alice", 9000, 123L, null, "test-secret");
        when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(instance));
        when(goosedProxy.proxy(any(), any(), eq(9000), eq("/schedules/list"), any())).thenReturn(Mono.empty());

        controller.catchAll(exchange).block();

        verify(instanceManager).getOrSpawn("test-agent", "alice");
        verify(goosedProxy).proxy(any(), any(), eq(9000), eq("/schedules/list"), any());
    }

    /**
     * Tests user access to system info route proxies.
     */
    @Test
    public void testUserAccessToSystemInfo_proxies() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/agents/test-agent/system_info").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(UserContextFilter.USER_ID_ATTR, "alice");

        ManagedInstance instance = new ManagedInstance("test-agent", "alice", 9000, 123L, null, "test-secret");
        when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(instance));
        when(goosedProxy.proxy(any(), any(), eq(9000), eq("/system_info"), any())).thenReturn(Mono.empty());

        controller.catchAll(exchange).block();

        verify(instanceManager).getOrSpawn("test-agent", "alice");
        verify(goosedProxy).proxy(any(), any(), eq(9000), eq("/system_info"), any());
    }

    /**
     * Tests ops gateway prefixed system info proxies.
     */
    @Test
    public void testOpsGatewayPrefixedSystemInfo_proxies() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/gateway/agents/test-agent/system_info").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(UserContextFilter.USER_ID_ATTR, "alice");

        ManagedInstance instance = new ManagedInstance("test-agent", "alice", 9000, 123L, null, "test-secret");
        when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(instance));
        when(goosedProxy.proxy(any(), any(), eq(9000), eq("/system_info"), any())).thenReturn(Mono.empty());

        controller.catchAll(exchange).block();

        verify(instanceManager).getOrSpawn("test-agent", "alice");
        verify(goosedProxy).proxy(any(), any(), eq(9000), eq("/system_info"), any());
    }

    /**
     * Tests short path returns404.
     */
    @Test
    public void testShortPath_returns404() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/agents/test-agent").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(UserContextFilter.USER_ID_ATTR, "admin");

        try {
            controller.catchAll(exchange).block();
            fail("Expected ResponseStatusException");
        } catch (ResponseStatusException ex) {
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }
    }

    /**
     * Tests query string forwarding.
     */
    @Test
    public void testQueryStringForwarding() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/agents/test-agent/schedules/list?limit=5").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(UserContextFilter.USER_ID_ATTR, "admin");

        ManagedInstance instance = new ManagedInstance("test-agent", "admin", 9000, 123L, null, "test-secret");
        when(instanceManager.getOrSpawn("test-agent", "admin")).thenReturn(Mono.just(instance));
        when(goosedProxy.proxy(any(), any(), eq(9000), eq("/schedules/list?limit=5"), any())).thenReturn(Mono.empty());

        controller.catchAll(exchange).block();

        verify(goosedProxy).proxy(any(), any(), eq(9000), eq("/schedules/list?limit=5"), any());
    }

    /**
     * Tests ops gateway prefixed query string forwarding.
     */
    @Test
    public void testOpsGatewayPrefixedQueryStringForwarding() {
        MockServerHttpRequest request =
            MockServerHttpRequest.get("/gateway/agents/test-agent/schedules/list?limit=5").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(UserContextFilter.USER_ID_ATTR, "admin");

        ManagedInstance instance = new ManagedInstance("test-agent", "admin", 9000, 123L, null, "test-secret");
        when(instanceManager.getOrSpawn("test-agent", "admin")).thenReturn(Mono.just(instance));
        when(goosedProxy.proxy(any(), any(), eq(9000), eq("/schedules/list?limit=5"), any())).thenReturn(Mono.empty());

        controller.catchAll(exchange).block();

        verify(goosedProxy).proxy(any(), any(), eq(9000), eq("/schedules/list?limit=5"), any());
    }

    /**
     * Tests user uses own user id for instance.
     */
    @Test
    public void testUserUsesOwnUserId() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/agents/test-agent/config/prompts").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(UserContextFilter.USER_ID_ATTR, "alice");

        ManagedInstance instance = new ManagedInstance("test-agent", "alice", 9000, 123L, null, "test-secret");
        when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(instance));
        when(goosedProxy.proxy(any(), any(), eq(9000), eq("/config/prompts"), any())).thenReturn(Mono.empty());

        controller.catchAll(exchange).block();

        verify(instanceManager).getOrSpawn("test-agent", "alice");
    }

    /**
     * Tests removed legacy reply path returns404 without proxying.
     */
    @Test
    public void testRemovedLegacyReplyPath_returns404WithoutProxying() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/agents/test-agent/reply").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(UserContextFilter.USER_ID_ATTR, "admin");

        try {
            controller.catchAll(exchange).block();
            fail("Expected ResponseStatusException");
        } catch (ResponseStatusException ex) {
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }

        verifyNoInteractions(instanceManager, goosedProxy);
    }

    /**
     * Tests removed legacy agent stop path returns404 without proxying.
     */
    @Test
    public void testRemovedLegacyAgentStopPath_returns404WithoutProxying() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/agents/test-agent/agent/stop").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(UserContextFilter.USER_ID_ATTR, "admin");

        try {
            controller.catchAll(exchange).block();
            fail("Expected ResponseStatusException");
        } catch (ResponseStatusException ex) {
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }

        verifyNoInteractions(instanceManager, goosedProxy);
    }
}

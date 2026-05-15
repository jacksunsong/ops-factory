/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.gateway.common.model.UserRole;
import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.process.PrewarmService;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import java.util.List;

/**
 * Test coverage for User Context Filter.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class UserContextFilterTest {
    private UserContextFilter filter;

    private GatewayProperties gatewayProperties;

    private PrewarmService prewarmService;

    /**
     * Sets the up.
     */
    @Before
    public void setUp() {
        prewarmService = mock(PrewarmService.class);
        gatewayProperties = mock(GatewayProperties.class);
        when(gatewayProperties.getAdminUsers()).thenReturn(List.of("admin"));
        filter = new UserContextFilter(prewarmService, gatewayProperties);
    }

    /**
     * Tests extracts user id from header.
     */
    @Test
    public void testExtractsUserIdFromHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").header("x-user-id", "user123").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> Mono.empty();
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals("user123", exchange.getAttribute(UserContextFilter.USER_ID_ATTR));
        assertEquals(UserRole.USER, exchange.getAttribute(UserContextFilter.USER_ROLE_ATTR));
    }

    /**
     * Tests rejects400 when no user id header.
     */
    @Test
    public void testRejects400WhenNoUserIdHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> Mono.empty();
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, exchange.getResponse().getStatusCode());
        assertNull(exchange.getAttribute(UserContextFilter.USER_ID_ATTR));
    }

    /**
     * Tests sys user gets admin role.
     */
    @Test
    public void testSysUserGetsAdminRole() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").header("x-user-id", "admin").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> Mono.empty();
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(UserRole.ADMIN, exchange.getAttribute(UserContextFilter.USER_ROLE_ATTR));
    }

    /**
     * Tests empty user id returns400.
     */
    @Test
    public void testEmptyUserIdReturns400() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").header("x-user-id", "").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> Mono.empty();
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, exchange.getResponse().getStatusCode());
        assertNull(exchange.getAttribute(UserContextFilter.USER_ID_ATTR));
    }

    /**
     * Tests configured admin user gets admin role.
     */
    @Test
    public void testConfiguredAdminUserGetsAdminRole() {
        when(gatewayProperties.getAdminUsers()).thenReturn(List.of("admin", "aiops"));

        MockServerHttpRequest request = MockServerHttpRequest.get("/test").header("x-user-id", "aiops").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> Mono.empty();
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(UserRole.ADMIN, exchange.getAttribute(UserContextFilter.USER_ROLE_ATTR));
    }

    /**
     * Tests non admin user gets user role.
     */
    @Test
    public void testNonAdminUserGetsUserRole() {
        when(gatewayProperties.getAdminUsers()).thenReturn(List.of("admin", "aiops"));

        MockServerHttpRequest request = MockServerHttpRequest.get("/test").header("x-user-id", "otheruser").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> Mono.empty();
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(UserRole.USER, exchange.getAttribute(UserContextFilter.USER_ROLE_ATTR));
    }

    /**
     * Tests trace start does not prewarm user.
     */
    @Test
    public void testTraceStartDoesNotPrewarmUser() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/gateway/agents/qa-agent/sessions/20260429_3/trace")
            .header("x-user-id", "admin")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> Mono.empty();
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals("admin", exchange.getAttribute(UserContextFilter.USER_ID_ATTR));
        assertEquals(UserRole.ADMIN, exchange.getAttribute(UserContextFilter.USER_ROLE_ATTR));
        verify(prewarmService, never()).onUserActivity("admin");
    }

    /**
     * Tests trace download does not prewarm user.
     */
    @Test
    public void testTraceDownloadDoesNotPrewarmUser() {
        MockServerHttpRequest request =
            MockServerHttpRequest.get("/gateway/session-traces/job-1/download").header("x-user-id", "admin").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> Mono.empty();
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals("admin", exchange.getAttribute(UserContextFilter.USER_ID_ATTR));
        assertEquals(UserRole.ADMIN, exchange.getAttribute(UserContextFilter.USER_ROLE_ATTR));
        verify(prewarmService, never()).onUserActivity("admin");
    }

    /**
     * Tests regular gateway request prewarms user.
     */
    @Test
    public void testRegularGatewayRequestPrewarmsUser() {
        MockServerHttpRequest request =
            MockServerHttpRequest.get("/gateway/agents").header("x-user-id", "admin").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> Mono.empty();
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(prewarmService).onUserActivity("admin");
    }
}

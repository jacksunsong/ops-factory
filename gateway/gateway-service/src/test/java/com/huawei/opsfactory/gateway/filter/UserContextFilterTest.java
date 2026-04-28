package com.huawei.opsfactory.gateway.filter;

import com.huawei.opsfactory.gateway.common.model.UserRole;
import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.process.PrewarmService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UserContextFilterTest {

    private UserContextFilter filter;
    private GatewayProperties gatewayProperties;

    @Before
    public void setUp() {
        PrewarmService prewarmService = mock(PrewarmService.class);
        gatewayProperties = mock(GatewayProperties.class);
        when(gatewayProperties.getAdminUsers()).thenReturn(List.of("admin"));
        filter = new UserContextFilter(prewarmService, gatewayProperties);
    }

    @Test
    public void testExtractsUserIdFromHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header("x-user-id", "user123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> Mono.empty();
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals("user123", exchange.getAttribute(UserContextFilter.USER_ID_ATTR));
        assertEquals(UserRole.USER, exchange.getAttribute(UserContextFilter.USER_ROLE_ATTR));
    }

    @Test
    public void testRejects400WhenNoUserIdHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> Mono.empty();
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST,
                exchange.getResponse().getStatusCode());
        assertNull(exchange.getAttribute(UserContextFilter.USER_ID_ATTR));
    }

    @Test
    public void testSysUserGetsAdminRole() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header("x-user-id", "admin")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> Mono.empty();
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(UserRole.ADMIN, exchange.getAttribute(UserContextFilter.USER_ROLE_ATTR));
    }

    @Test
    public void testEmptyUserIdReturns400() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header("x-user-id", "")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> Mono.empty();
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST,
                exchange.getResponse().getStatusCode());
        assertNull(exchange.getAttribute(UserContextFilter.USER_ID_ATTR));
    }

    @Test
    public void testConfiguredAdminUserGetsAdminRole() {
        when(gatewayProperties.getAdminUsers()).thenReturn(List.of("admin", "aiops"));

        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header("x-user-id", "aiops")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> Mono.empty();
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(UserRole.ADMIN, exchange.getAttribute(UserContextFilter.USER_ROLE_ATTR));
    }

    @Test
    public void testNonAdminUserGetsUserRole() {
        when(gatewayProperties.getAdminUsers()).thenReturn(List.of("admin", "aiops"));

        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header("x-user-id", "otheruser")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> Mono.empty();
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(UserRole.USER, exchange.getAttribute(UserContextFilter.USER_ROLE_ATTR));
    }
}
